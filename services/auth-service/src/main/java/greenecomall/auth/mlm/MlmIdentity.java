package greenecomall.auth.mlm;

import java.time.Instant;

/**
 * Данные пользователя, извлечённые из проверенного SSO-токена внешней MLM-системы.
 * Обязательны {@code jti} и {@code mlmUserId}; остальное — опционально (префилл профиля).
 */
public record MlmIdentity(
        String jti,
        Instant issuedAt,
        String mlmUserId,
        String referralCode,
        String uplineMlmUserId,
        String accessStatus,
        String email,
        String phone,
        String fullName,
        String locale,
        String countryCode,
        String city) {
}
