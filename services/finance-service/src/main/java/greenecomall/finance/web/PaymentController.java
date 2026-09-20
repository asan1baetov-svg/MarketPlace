package greenecomall.finance.web;

import greenecomall.common.domain.DomainException;
import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.security.Authz;
import greenecomall.finance.FinanceErrors;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.mlm.MlmTariffClient;
import greenecomall.finance.payment.PaymentService;
import greenecomall.finance.web.dto.CreateMlmAccessPaymentRequest;
import greenecomall.finance.web.dto.CreateOrderPaymentRequest;
import greenecomall.finance.web.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Платежи. Платёж за заказ создаётся автоматически из {@code orders.OrderCreated}; служебный
 * {@code POST /internal/payments} — тот же путь для order-service (идемпотентен по orderId).
 * Клиент видит только свои платежи; сумму доступа к MLM назначает тариф, а не клиент.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final MlmTariffClient mlmTariffs;

    public PaymentController(PaymentService paymentService, MlmTariffClient mlmTariffs) {
        this.paymentService = paymentService;
        this.mlmTariffs = mlmTariffs;
    }

    @PostMapping("/internal/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createForOrder(@Valid @RequestBody CreateOrderPaymentRequest request) {
        Payment payment = paymentService.createOrderPayment(
                request.orderId(), request.clientUserId(), request.amountMinor(), request.currency());
        return toResponse(payment);
    }

    @PostMapping("/payments/mlm-access")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createForMlmAccess(@Valid @RequestBody CreateMlmAccessPaymentRequest request,
                                              AuthPrincipal principal) {
        Authz.require(principal);
        if (principal.mlmUserId() == null) {
            throw new DomainException(FinanceErrors.MLM_ACCOUNT_REQUIRED, "only MLM users can pay for MLM access");
        }
        MlmTariffClient.Tariff tariff = mlmTariffs.payableTariff(request.tariffId());
        Payment payment = paymentService.createMlmAccessPayment(
                principal.mlmUserId(), tariff.id(), principal.userId(), tariff.accessPriceMinor(), tariff.currency());
        return toResponse(payment);
    }

    @GetMapping("/payments/{id}")
    public PaymentResponse get(@PathVariable UUID id, AuthPrincipal principal) {
        Payment payment = paymentService.get(id);
        Authz.requireSelfOrAdmin(principal, payment.getClientUserId());
        return toResponse(payment);
    }

    /** Платёж по заказу — клиент получает ссылку на страницу оплаты после checkout. */
    @GetMapping("/payments/by-order/{orderId}")
    public PaymentResponse byOrder(@PathVariable UUID orderId, AuthPrincipal principal) {
        Payment payment = paymentService.getByOrder(orderId);
        Authz.requireSelfOrAdmin(principal, payment.getClientUserId());
        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        Payment withPage = paymentService.ensureHostedPage(payment);
        return PaymentResponse.from(withPage, paymentService.hostedPageUrl(withPage));
    }
}
