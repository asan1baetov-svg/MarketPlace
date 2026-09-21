package greenecomall.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки auth-service (префикс {@code auth} в application.yml).
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, Refresh refresh, Otp otp, MlmSso mlmSso, Outbox outbox, Internal internal,
                             BootstrapAdmin bootstrapAdmin) {

    public record Jwt(String issuer, String audience, Duration accessTokenTtl) {
    }

    /**
     * Первый администратор, создаётся при старте, если такого email ещё нет (см. {@code BootstrapAdmin}).
     *
     * @param superAdmin выдать ещё и роль SUPER_ADMIN
     */
    public record BootstrapAdmin(String email, String password, boolean superAdmin) {
    }

    public record Refresh(Duration ttl) {
    }

    public record Otp(Duration ttl, Duration resendInterval, int length) {
    }

    /**
     * Параметры проверки входящего SSO-токена от внешней MLM-системы.
     *
     * @param algorithm HMAC-алгоритм подписи (HS256/HS384/HS512). Реальный MLM-бэк подписывает через
     *                  jjwt {@code Keys.hmacShaKeyFor(secret)}, который выбирает алгоритм по длине ключа —
     *                  значение должно совпасть с их секретом (см. docs/ARCHITECTURE.md §5)
     */
    public record MlmSso(String sharedSecret, String issuer, String audience, Duration clockSkew, String algorithm) {

        public MlmSso {
            if (algorithm == null || algorithm.isBlank()) {
                algorithm = "HS256";
            }
        }
    }

    public record Outbox(Duration pollInterval) {
    }

    /** Служебный токен для доступа к {@code /internal/**} в обход JWT (см. {@code X-Internal-Token}). */
    public record Internal(String token) {
    }
}
