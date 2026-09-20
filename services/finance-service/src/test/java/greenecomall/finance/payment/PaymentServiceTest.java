package greenecomall.finance.payment;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.finance.FinanceErrors;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.domain.PaymentStatus;
import greenecomall.finance.repo.PaymentEventRepository;
import greenecomall.finance.repo.PaymentRepository;
import greenecomall.finance.repo.RefundRepository;
import greenecomall.finance.wallet.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private PaymentEventRepository paymentEvents;
    @Mock private RefundRepository refunds;
    @Mock private PaymentProvider provider;
    @Mock private SettlementService settlementService;
    @Mock private DomainEventPublisher events;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, paymentEvents, refunds, provider, settlementService, events,
                JsonMapper.builder().build(), Clock.systemUTC());
    }

    private static String body(String eventId, String providerPaymentId, String status) {
        return "{\"providerEventId\":\"" + eventId + "\",\"providerPaymentId\":\"" + providerPaymentId
                + "\",\"status\":\"" + status + "\"}";
    }

    /** Разбор тела mock-формата — как это делает провайдер, подпись задаётся тестом. */
    private static PaymentProvider.WebhookEvent parsed(String raw, boolean signatureValid) {
        var json = JsonMapper.builder().build().readTree(raw);
        return new PaymentProvider.WebhookEvent(signatureValid, json.get("providerEventId").asString(),
                json.get("providerPaymentId").asString(), json.get("status").asString(), null, null);
    }

    @Test
    void webhook_amountDifferentFromPayment_isNotCountedAsPaid() {
        String raw = body("e3", "p3", "succeeded");
        Payment payment = Payment.forOrder(UUID.randomUUID(), UUID.randomUUID(), 29_000, "KGS", "finik");
        when(provider.parseWebhook(any(), eq(raw))).thenReturn(
                new PaymentProvider.WebhookEvent(true, "e3", "p3", "SUCCEEDED", null, 1_000L));
        when(paymentEvents.existsByProviderEventId("e3")).thenReturn(false);
        when(payments.findByProviderPaymentId("p3")).thenReturn(Optional.of(payment));

        service.handleWebhook(new HttpHeaders(), raw);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(settlementService, never()).settleOrderPaid(any(), any());
    }

    @Test
    void webhook_invalidSignature_isRejectedButLogged() {
        String raw = body("e1", "p1", "succeeded");
        when(provider.parseWebhook(any(), eq(raw))).thenReturn(parsed(raw, false));
        when(paymentEvents.existsByProviderEventId("e1")).thenReturn(false);
        when(payments.findByProviderPaymentId("p1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleWebhook(new HttpHeaders(), raw))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(FinanceErrors.WEBHOOK_SIGNATURE_INVALID);
        verify(paymentEvents).save(any());
        verify(events, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void webhook_duplicateProviderEvent_isIgnored() {
        String raw = body("e1", "p1", "succeeded");
        when(provider.parseWebhook(any(), eq(raw))).thenReturn(parsed(raw, true));
        when(paymentEvents.existsByProviderEventId("e1")).thenReturn(true);

        service.handleWebhook(new HttpHeaders(), raw);

        verify(paymentEvents, never()).save(any());
        verify(settlementService, never()).settleOrderPaid(any(), any());
    }

    @Test
    void webhook_succeeded_marksPaidPublishesOrderPaidAndSettles() {
        String raw = body("e1", "p1", "succeeded");
        Payment payment = Payment.forOrder(UUID.randomUUID(), UUID.randomUUID(), 29_000, "KGS", "mock");
        when(provider.parseWebhook(any(), eq(raw))).thenReturn(parsed(raw, true));
        when(paymentEvents.existsByProviderEventId("e1")).thenReturn(false);
        when(payments.findByProviderPaymentId("p1")).thenReturn(Optional.of(payment));

        service.handleWebhook(new HttpHeaders(), raw);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        ArgumentCaptor<EventEnvelope<?>> envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events).publish(eq("payments"), any(), envelope.capture());
        assertThat(envelope.getValue().eventType()).isEqualTo(EventTypes.ORDER_PAID);
        verify(settlementService).settleOrderPaid(eq(payment), any());
    }

    @Test
    void webhook_failed_publishesPaymentFailed() {
        String raw = body("e2", "p2", "failed");
        Payment payment = Payment.forOrder(UUID.randomUUID(), UUID.randomUUID(), 1_000, "KGS", "mock");
        when(provider.parseWebhook(any(), eq(raw))).thenReturn(parsed(raw, true));
        when(paymentEvents.existsByProviderEventId("e2")).thenReturn(false);
        when(payments.findByProviderPaymentId("p2")).thenReturn(Optional.of(payment));

        service.handleWebhook(new HttpHeaders(), raw);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        ArgumentCaptor<EventEnvelope<?>> envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events).publish(eq("payments"), any(), envelope.capture());
        assertThat(envelope.getValue().eventType()).isEqualTo(EventTypes.PAYMENT_FAILED);
        verify(settlementService, never()).settleOrderPaid(any(), any());
    }
}
