package greenecomall.gateway.security;

import greenecomall.common.security.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Resource-server: проверяет access-JWT, выпущенный auth-service (JWKS, см. application.yml),
 * пропускает публичные пути без токена, для остальных требует валидный Bearer-токен; {@code /api/admin/**}
 * — только ADMIN/SUPER_ADMIN (роли из claim {@code roles}). После аутентификации
 * {@link GatewayIdentityPropagationFilter} проставляет вниз {@code X-User-*};
 * {@link GatewayHeaderStrippingFilter} вырезает эти заголовки из входящего запроса.
 * Проксирование — {@code greenecomall.gateway.routing.ProxyHandler}.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final String[] PUBLIC_POST_PATHS = {
            "/api/auth/register", "/api/auth/otp/**", "/api/auth/login",
            "/api/auth/token/refresh", "/api/auth/logout", "/api/auth/sso/mlm",
            // webhooks защищены подписью отправителя (эквайринг, MLM-бэк), а не JWT
            "/api/webhooks/**"
    };

    private static final String[] PUBLIC_GET_PATHS = {"/api/catalog/**"};

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         CorsConfigurationSource corsConfigurationSource) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchange -> exchange
                        // preflight браузера уходит без токена — иначе ни один фронт не достучится
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/", "/actuator/**").permitAll()
                        .pathMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .pathMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .pathMatchers("/api/admin/**").hasAnyRole(Roles.ADMIN, Roles.SUPER_ADMIN)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter())))
                .addFilterAfter(new GatewayIdentityPropagationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * CORS для браузерных фронтов (покупатель, магазин, курьер, админка) на других доменах.
     * Разрешённые источники — {@code gateway.cors.allowed-origins} (через запятую, по умолчанию любой:
     * токен ходит в заголовке {@code Authorization}, а не в cookie, поэтому учётные данные не шлются).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${gateway.cors.allowed-origins:*}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Claim {@code roles} (["CLIENT_EXTERNAL", "ADMIN", ...]) → authorities {@code ROLE_*}. */
    private static ReactiveJwtAuthenticationConverterAdapter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix(Roles.AUTHORITY_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
