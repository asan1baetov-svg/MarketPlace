package greenecomall.gateway.security;

import greenecomall.common.security.GatewayHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * После успешной проверки JWT (см. {@code GatewaySecurityConfig}) проставляет вниз
 * {@code X-User-Id}/{@code X-User-Roles}/{@code X-Client-Type}/{@code X-Trace-Id} — сервисы за
 * гейтвеем доверяют этим заголовкам, не проверяя JWT повторно. На публичных путях без токена
 * заголовки не проставляются (запрос идёт как есть, без identity).
 */
public class GatewayIdentityPropagationFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication()))
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(this::identityHeaders)
                .defaultIfEmpty(Map.of())
                .flatMap(headers -> {
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(h -> headers.forEach(h::set))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    private Map<String, String> identityHeaders(Jwt jwt) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(GatewayHeaders.USER_ID, jwt.getSubject());
        List<String> roles = jwt.getClaimAsStringList("roles");
        headers.put(GatewayHeaders.USER_ROLES, roles != null ? String.join(",", roles) : "");
        String clientType = jwt.getClaimAsString("client_type");
        if (clientType != null) {
            headers.put(GatewayHeaders.CLIENT_TYPE, clientType);
        }
        headers.put(GatewayHeaders.TRACE_ID, UUID.randomUUID().toString());
        return headers;
    }
}
