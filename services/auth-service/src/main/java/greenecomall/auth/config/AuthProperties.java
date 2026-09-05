package greenecomall.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки auth-service (префикс {@code auth} в application.yml).
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, Refresh refresh, Otp otp, MlmSso mlmSso, Outbox outbox, Internal internal) {

    public record Jwt(String issuer, String audience, Duration accessTokenTtl) {
    }

    public record Refresh(Duration ttl) {
    }

    public record Otp(Duration ttl, Duration resendInterval, int length) {
    }

    /** Параметры проверки входящего SSO-токена от внешней MLM-системы. */
    public record MlmSso(String sharedSecret, String issuer, String audience, Duration clockSkew) {
    }

    public record Outbox(Duration pollInterval) {
    }

    /** Служебный токен для доступа к {@code /internal/**} в обход JWT (см. {@code X-Internal-Token}). */
    public record Internal(String token) {
    }
}
