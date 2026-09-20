package greenecomall.gateway;

import greenecomall.gateway.routing.GatewayRoutingProperties;
import greenecomall.gateway.routing.ProxyHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Маршрутизация: всё под {@code /api/**} проксируется на сервисы по {@code RouteTable}
 * (адреса — {@code gateway.services.*}). Проверка JWT и проброс {@code X-User-*} — в
 * {@code GatewaySecurityConfig}. Rate limit (Redis) и circuit breaker (Resilience4j) — следующим шагом.
 */
@Configuration
@EnableConfigurationProperties(GatewayRoutingProperties.class)
public class GatewayRoutes {

    @Bean
    public RouterFunction<ServerResponse> health() {
        return route(GET("/"), req -> ServerResponse.ok().bodyValue("green-eco-mall api-gateway"));
    }

    @Bean
    public RouterFunction<ServerResponse> apiProxy(ProxyHandler proxyHandler) {
        return route(path("/api/**"), proxyHandler::proxy);
    }
}
