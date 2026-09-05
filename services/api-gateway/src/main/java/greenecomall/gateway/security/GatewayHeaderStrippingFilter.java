package greenecomall.gateway.security;

import greenecomall.common.security.GatewayHeaders;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Вырезает identity-заголовки из входящего запроса до того, как он дойдёт до аутентификации
 * или до маршрутизации вниз — иначе клиент мог бы подделать {@code X-User-Id} и т.п.
 * Настоящие значения этих заголовков проставляет {@link GatewayIdentityPropagationFilter}
 * уже после проверки JWT.
 */
@Component
public class GatewayHeaderStrippingFilter implements WebFilter, Ordered {

    private static final List<String> IDENTITY_HEADERS = List.of(
            GatewayHeaders.USER_ID, GatewayHeaders.USER_ROLES,
            GatewayHeaders.CLIENT_TYPE, GatewayHeaders.TRACE_ID);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> IDENTITY_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(stripped).build());
    }
}
