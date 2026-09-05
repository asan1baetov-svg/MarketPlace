package greenecomall.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Resource-server: проверяет access-JWT, выпущенный auth-service (JWKS, см. application.yml),
 * пропускает публичные пути без токена, для остальных требует валидный Bearer-токен.
 * После аутентификации {@link GatewayIdentityPropagationFilter} проставляет вниз
 * {@code X-User-*}; {@link GatewayHeaderStrippingFilter} (глобальный {@code WebFilter}) вырезает
 * эти же заголовки из входящего запроса до того, как дело дойдёт до этой цепочки.
 *
 * Реальная маршрутизация на сервисы пока не подключена (см. TODO в pom.xml про Spring Cloud
 * Gateway) — здесь только security-фильтр.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final String[] PUBLIC_POST_PATHS = {
            "/api/auth/register", "/api/auth/otp/**", "/api/auth/login",
            "/api/auth/token/refresh", "/api/auth/logout", "/api/auth/sso/mlm"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/", "/actuator/**").permitAll()
                        .pathMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterAfter(new GatewayIdentityPropagationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
