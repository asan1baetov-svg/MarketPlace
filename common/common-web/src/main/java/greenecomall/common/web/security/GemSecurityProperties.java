package greenecomall.common.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Общие настройки безопасности сервисов (префикс {@code gem.security}).
 *
 * @param jwt           проверка access-JWT, выпущенного auth-service
 * @param internalToken общий служебный токен для {@code /internal/**} (заголовок {@code X-Internal-Token});
 *                      пустое значение закрывает {@code /internal/**} полностью
 */
@ConfigurationProperties(prefix = "gem.security")
public record GemSecurityProperties(Jwt jwt, String internalToken) {

    public GemSecurityProperties {
        if (jwt == null) {
            jwt = new Jwt(null, null, null, null);
        }
    }

    /**
     * @param jwkSetUri JWKS auth-service; ключи подгружаются лениво при первом запросе и кэшируются
     * @param issuer    ожидаемый {@code iss} (= {@code auth.jwt.issuer} в auth-service)
     * @param audience  ожидаемый {@code aud}
     * @param clockSkew допуск расхождения часов для {@code exp}/{@code nbf}
     */
    public record Jwt(String jwkSetUri, String issuer, String audience, Duration clockSkew) {

        public Jwt {
            if (jwkSetUri == null || jwkSetUri.isBlank()) {
                jwkSetUri = "http://localhost:8081/oauth2/jwks";
            }
            if (issuer == null || issuer.isBlank()) {
                issuer = "http://localhost:8081";
            }
            if (audience == null || audience.isBlank()) {
                audience = "green-eco-mall";
            }
            if (clockSkew == null) {
                clockSkew = Duration.ofSeconds(60);
            }
        }
    }
}
