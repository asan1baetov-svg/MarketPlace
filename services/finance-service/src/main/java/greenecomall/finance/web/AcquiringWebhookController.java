package greenecomall.finance.web;

import greenecomall.finance.payment.PaymentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook эквайринга. Подпись проверяется в {@link PaymentService} (не Spring Security);
 * идемпотентность — по {@code providerEventId} в теле (docs/ARCHITECTURE.md §7.1, R3).
 */
@RestController
public class AcquiringWebhookController {

    private final PaymentService paymentService;

    public AcquiringWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/webhooks/acquiring/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handle(@PathVariable String provider, @RequestHeader HttpHeaders headers,
                       @RequestBody String rawBody) {
        paymentService.handleWebhook(headers, rawBody);
    }
}
