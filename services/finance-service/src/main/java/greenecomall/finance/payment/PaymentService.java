package greenecomall.finance.payment;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.PaymentEvents;
import greenecomall.finance.FinanceErrors;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.domain.PaymentEvent;
import greenecomall.finance.domain.PaymentStatus;
import greenecomall.finance.domain.PaymentType;
import greenecomall.finance.domain.Refund;
import greenecomall.finance.repo.PaymentEventRepository;
import greenecomall.finance.repo.PaymentRepository;
import greenecomall.finance.repo.RefundRepository;
import greenecomall.finance.support.Tracing;
import greenecomall.finance.wallet.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Приём онлайн-оплаты через эквайринг: создание платёжного намерения, обработка webhook
 * (подпись + идемпотентность по {@code provider_event_id}), публикация {@code OrderPaid} /
 * {@code PaymentFailed} / {@code MlmAccessPaid}, возвраты.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String PRODUCER = "finance-service";

    private final PaymentRepository payments;
    private final PaymentEventRepository paymentEvents;
    private final RefundRepository refunds;
    private final PaymentProvider provider;
    private final SettlementService settlementService;
    private final DomainEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentEventRepository paymentEvents, RefundRepository refunds,
                          PaymentProvider provider, SettlementService settlementService, DomainEventPublisher events,
                          ObjectMapper objectMapper, Clock clock) {
        this.payments = payments;
        this.paymentEvents = paymentEvents;
        this.refunds = refunds;
        this.provider = provider;
        this.settlementService = settlementService;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Payment createOrderPayment(UUID orderId, UUID clientUserId, long amountMinor, String currency) {
        Payment existing = payments.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return existing;
        }
        if (amountMinor <= 0) {
            throw new DomainException(FinanceErrors.PAYMENT_AMOUNT_INVALID, "amount must be positive: " + amountMinor);
        }
        Payment payment = Payment.forOrder(orderId, clientUserId, amountMinor, currency, provider.name());
        return initiate(payment);
    }

    @Transactional
    public Payment createMlmAccessPayment(String mlmUserId, UUID tariffId, UUID clientUserId,
                                          long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw new DomainException(FinanceErrors.PAYMENT_AMOUNT_INVALID, "amount must be positive: " + amountMinor);
        }
        Payment payment = Payment.forMlmAccess(mlmUserId, tariffId, clientUserId, amountMinor, currency, provider.name());
        return initiate(payment);
    }

    private Payment initiate(Payment payment) {
        payments.save(payment);
        requestHostedPage(payment);
        events.publish(Topics.PAYMENTS, partitionKey(payment),
                EventEnvelope.of(EventTypes.PAYMENT_INITIATED, PRODUCER, Tracing.currentTraceId(),
                        new InitiatedPayload(payment.getId(), payment.getType().name(),
                                payment.getOrderId(), payment.getMlmUserId(), payment.getAmountMinor(),
                                payment.getCurrency())));
        return payment;
    }

    /**
     * Ссылка на оплату у провайдера. Сбой провайдера не роняет создание платежа (иначе событие
     * {@code OrderCreated} ушло бы в ретраи и потерялось): платёж остаётся PENDING без ссылки,
     * она запрашивается повторно, когда клиент откроет платёж ({@link #ensureHostedPage}).
     */
    private void requestHostedPage(Payment payment) {
        try {
            PaymentProvider.HostedPage page = provider.initiate(payment);
            payment.attachHostedPage(page.providerPaymentId(), page.redirectUrl());
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("payment {}: provider {} did not create a payment page yet: {}", payment.getId(),
                    provider.name(), e.toString());
        }
    }

    /** Для ещё не оплаченного платежа без ссылки — повторная попытка у провайдера. */
    @Transactional
    public Payment ensureHostedPage(Payment payment) {
        if (payment.isPending() && payment.getHostedPageUrl() == null) {
            Payment managed = get(payment.getId());
            requestHostedPage(managed);
            return managed;
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id)
                .orElseThrow(() -> new DomainException(FinanceErrors.PAYMENT_NOT_FOUND, "payment not found: " + id));
    }

    @Transactional(readOnly = true)
    public Payment getByOrder(UUID orderId) {
        return payments.findByOrderId(orderId)
                .orElseThrow(() -> new DomainException(FinanceErrors.PAYMENT_NOT_FOUND, "no payment for order " + orderId));
    }

    public String hostedPageUrl(Payment payment) {
        return payment.getHostedPageUrl();
    }

    /**
     * Обработка webhook эквайринга. Подпись и формат — у провайдера ({@link PaymentProvider#parseWebhook});
     * идемпотентность — по {@code providerEventId}; успех засчитывается, только если провайдер
     * списал ровно сумму платежа.
     */
    @Transactional
    public void handleWebhook(HttpHeaders headers, String rawBody) {
        PaymentProvider.WebhookEvent event = provider.parseWebhook(headers, rawBody);
        String providerEventId = event.providerEventId();
        String status = event.status();

        if (providerEventId != null && paymentEvents.existsByProviderEventId(providerEventId)) {
            log.debug("webhook {} already processed", providerEventId);
            return;
        }

        Payment payment = event.providerPaymentId() == null ? null
                : payments.findByProviderPaymentId(event.providerPaymentId()).orElse(null);
        paymentEvents.save(new PaymentEvent(
                payment == null ? null : payment.getId(),
                providerEventId != null ? providerEventId : UUID.randomUUID().toString(),
                rawBody, event.signatureValid()));

        if (!event.signatureValid()) {
            throw new DomainException(FinanceErrors.WEBHOOK_SIGNATURE_INVALID, "invalid webhook signature");
        }
        if (payment == null) {
            throw new DomainException(FinanceErrors.WEBHOOK_UNKNOWN_PAYMENT,
                    "no payment for providerPaymentId " + event.providerPaymentId());
        }
        boolean succeeded = "succeeded".equalsIgnoreCase(status);
        if (succeeded && event.amountMinor() != null && event.amountMinor() != payment.getAmountMinor()) {
            // Не засчитываем: заказ не должен считаться оплаченным при недоплате. Разбор — вручную.
            log.error("payment {}: provider captured {} but payment is {} — left PENDING for review",
                    payment.getId(), event.amountMinor(), payment.getAmountMinor());
            return;
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED && succeeded) {
            refundLateCapture(payment);
            return;
        }
        if (!payment.isPending()) {
            log.info("webhook for payment {} ignored: already {}", payment.getId(), payment.getStatus());
            return;
        }

        if (succeeded) {
            applySuccess(payment);
        } else if ("failed".equalsIgnoreCase(status)) {
            applyFailure(payment, event.reason());
        } else {
            log.warn("webhook for payment {}: unknown status '{}'", payment.getId(), status);
        }
    }

    private void applySuccess(Payment payment) {
        payment.markSucceeded();
        Instant paidAt = Instant.now(clock);
        if (payment.getType() == PaymentType.ORDER) {
            events.publish(Topics.PAYMENTS, partitionKey(payment),
                    EventEnvelope.of(EventTypes.ORDER_PAID, PRODUCER, Tracing.currentTraceId(),
                            new PaymentEvents.OrderPaid(payment.getId(), payment.getOrderId(),
                                    payment.getAmountMinor(), payment.getCurrency(), paidAt)));
            settlementService.settleOrderPaid(payment, payment.getId());
        } else {
            events.publish(Topics.PAYMENTS, payment.getMlmUserId(),
                    EventEnvelope.of(EventTypes.MLM_ACCESS_PAID, PRODUCER, Tracing.currentTraceId(),
                            new PaymentEvents.MlmAccessPaid(payment.getId(), payment.getMlmUserId(),
                                    payment.getMlmTariffId(), payment.getAmountMinor(), payment.getCurrency(), paidAt)));
        }
    }

    private void applyFailure(Payment payment, String reason) {
        payment.markFailed();
        if (payment.getType() == PaymentType.ORDER) {
            events.publish(Topics.PAYMENTS, partitionKey(payment),
                    EventEnvelope.of(EventTypes.PAYMENT_FAILED, PRODUCER, Tracing.currentTraceId(),
                            new PaymentEvents.PaymentFailed(payment.getId(), payment.getOrderId(),
                                    reason != null ? reason : "declined")));
        }
    }

    /**
     * Заказ отменён ({@code orders.OrderCancelled}, в т.ч. админский возврат): неоплаченный платёж
     * закрывается, оплаченный возвращается клиенту (с обратными проводками по кошелькам).
     */
    @Transactional
    public void onOrderCancelled(UUID orderId, String reason, UUID eventId) {
        Payment payment = payments.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            return;
        }
        switch (payment.getStatus()) {
            case PENDING -> payment.cancel();
            case SUCCEEDED -> refund(payment.getId(), reason);
            default -> settlementService.reverseOrder(orderId, eventId);
        }
    }

    private void refundLateCapture(Payment payment) {
        Refund refund = refunds.save(new Refund(payment.getId(), payment.getAmountMinor(), "captured after order cancellation"));
        payment.markLateCaptureRefunded();
        provider.refund(payment, payment.getAmountMinor()).ifPresent(refund::markSucceeded);
        log.warn("payment {} captured after order {} was cancelled — refunded {}", payment.getId(),
                payment.getOrderId(), refund.getId());
    }

    @Transactional
    public Refund refund(UUID paymentId, String reason) {
        Payment payment = get(paymentId);
        Refund refund = refunds.save(new Refund(paymentId, payment.getAmountMinor(),
                reason != null ? reason : "admin refund"));
        payment.markRefunded();
        // Нет API возврата у провайдера (Finik) — возврат остаётся REQUESTED до ручного подтверждения админом
        provider.refund(payment, payment.getAmountMinor()).ifPresent(refund::markSucceeded);
        if (payment.getType() == PaymentType.ORDER) {
            events.publish(Topics.PAYMENTS, partitionKey(payment),
                    EventEnvelope.of(EventTypes.PAYMENT_REFUNDED, PRODUCER, Tracing.currentTraceId(),
                            new PaymentEvents.PaymentRefunded(payment.getId(), payment.getOrderId(),
                                    payment.getAmountMinor(), payment.getCurrency())));
            settlementService.reverseOrder(payment.getOrderId(), refund.getId());
        }
        return refund;
    }

    @Transactional(readOnly = true)
    public Page<Refund> refundsByStatus(Refund.Status status, Pageable pageable) {
        return refunds.findByStatusOrderByCreatedAtAsc(status, pageable);
    }

    /** Админ подтверждает, что вернул деньги клиенту в кабинете провайдера. */
    @Transactional
    public Refund completeManualRefund(UUID refundId, String reference) {
        Refund refund = refunds.findById(refundId)
                .orElseThrow(() -> new DomainException(FinanceErrors.PAYMENT_NOT_FOUND, "refund not found: " + refundId));
        refund.completeManually(reference);
        return refund;
    }

    /** Ключ партиции = корневой агрегат: заказ для товарных платежей, MLM-пользователь для оплаты доступа. */
    private static String partitionKey(Payment payment) {
        if (payment.getOrderId() != null) {
            return payment.getOrderId().toString();
        }
        return payment.getMlmUserId() != null ? payment.getMlmUserId() : String.valueOf(payment.getId());
    }


    /** Payload {@code payments.PaymentInitiated} (в common-events отдельного record нет). */
    public record InitiatedPayload(UUID paymentId, String type, UUID orderId, String mlmUserId,
                                   long amountMinor, String currency) {
    }
}
