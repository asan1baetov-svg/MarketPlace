package greenecomall.finance.web;

import greenecomall.finance.payment.MockAcquiringProvider;
import greenecomall.finance.payment.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Локальная «страница оплаты» mock-эквайринга: {@code POST /mock-acquiring/pay/{providerPaymentId}?outcome=succeeded}
 * формирует подписанное тело и прогоняет его через тот же обработчик webhook, что и настоящий провайдер.
 * Только для разработки/тестов.
 */
@RestController
// Страница «оплатить» без денег: только для dev/тестовых стендов, на проде — finance.mock-acquiring.simulator-enabled=false
@ConditionalOnProperty(prefix = "finance.mock-acquiring", name = "simulator-enabled", havingValue = "true",
        matchIfMissing = true)
public class MockAcquiringController {

    private final PaymentService paymentService;
    private final greenecomall.finance.config.FinanceProperties properties;

    public MockAcquiringController(PaymentService paymentService,
                                   greenecomall.finance.config.FinanceProperties properties) {
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @PostMapping("/mock-acquiring/pay/{providerPaymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pay(@PathVariable String providerPaymentId,
                    @RequestParam(defaultValue = "succeeded") String outcome) {
        String body = "{\"providerEventId\":\"" + UUID.randomUUID()
                + "\",\"providerPaymentId\":\"" + providerPaymentId
                + "\",\"status\":\"" + outcome + "\"}";
        String signature = MockAcquiringProvider.hmacSha256Hex(properties.mockWebhookSecret(), body);
        HttpHeaders headers = new HttpHeaders();
        headers.set(MockAcquiringProvider.SIGNATURE_HEADER, signature);
        paymentService.handleWebhook(headers, body);
    }
}
