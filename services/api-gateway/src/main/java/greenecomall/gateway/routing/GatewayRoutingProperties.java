package greenecomall.gateway.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Адреса сервисов за гейтвеем (ключ — логическое имя из {@link RouteTable}).
 *
 * @param services       {@code auth -> http://auth-service:8081} и т.д.
 * @param responseTimeout таймаут ответа upstream
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayRoutingProperties(Map<String, String> services, Duration responseTimeout) {

    public GatewayRoutingProperties {
        if (services == null) {
            services = Map.of();
        }
        if (responseTimeout == null) {
            responseTimeout = Duration.ofSeconds(30);
        }
    }
}
