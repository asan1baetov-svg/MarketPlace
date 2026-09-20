package greenecomall.mlm.web;

import greenecomall.common.domain.DomainException;
import greenecomall.mlm.MlmErrors;
import greenecomall.mlm.config.MlmProperties;
import greenecomall.mlm.core.AccountService;
import greenecomall.mlm.integration.HmacSigner;
import greenecomall.mlm.web.dto.MlmDtos.AccountStatusWebhook;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Входящий webhook от внешнего MLM-бэка: «у пользователя изменился статус» (прежде всего —
 * оплачен входной взнос через Finik на их стороне). Подпись — та же схема, что у наших
 * обратных вызовов: {@code HMAC-SHA256(secret, X-Timestamp + "." + body)}; допуск по времени 5 минут.
 */
@RestController
public class MlmWebhookController {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final AccountService accounts;
    private final MlmProperties properties;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;

    public MlmWebhookController(AccountService accounts, MlmProperties properties, ObjectMapper objectMapper,
                                Validator validator, Clock clock) {
        this.accounts = accounts;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

    @PostMapping("/webhooks/mlm/account-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accountStatus(@RequestHeader(value = HmacSigner.TIMESTAMP_HEADER, required = false) String timestamp,
                              @RequestHeader(value = HmacSigner.SIGNATURE_HEADER, required = false) String signature,
                              @RequestBody String rawBody) {
        if (!freshTimestamp(timestamp)
                || !HmacSigner.verify(properties.sync().sharedSecret(), timestamp, rawBody, signature)) {
            throw new DomainException(MlmErrors.WEBHOOK_SIGNATURE_INVALID, "invalid MLM webhook signature");
        }
        AccountStatusWebhook body = objectMapper.readValue(rawBody, AccountStatusWebhook.class);
        Set<ConstraintViolation<AccountStatusWebhook>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new DomainException(MlmErrors.WEBHOOK_BODY_INVALID, "invalid webhook body: " + violations);
        }
        accounts.applyExternalStatus(body.mlmUserId(), body.status(), body.tariffId());
    }

    private boolean freshTimestamp(String timestamp) {
        try {
            Instant sent = Instant.ofEpochSecond(Long.parseLong(timestamp));
            return Duration.between(sent, clock.instant()).abs().compareTo(MAX_CLOCK_SKEW) <= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
