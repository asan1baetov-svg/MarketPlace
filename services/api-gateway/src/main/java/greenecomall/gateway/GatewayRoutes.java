package greenecomall.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Временная маршрутизация до подключения Spring Cloud Gateway (см. TODO в pom.xml).
 *
 * Целевая карта маршрутов:
 *   /api/auth/**          -> auth-service:8081
 *   /api/catalog/**       -> catalog-service:8082
 *   /api/orders/**, /api/cart/**  -> order-service:8083
 *   /api/payments/**, /api/wallet/**, /webhooks/** -> finance-service:8084
 *   /api/couriers/**, /api/assignments/** -> courier-service:8085
 *   /api/mlm/**           -> mlm-service:8086
 *   /api/notifications/** -> notification-service:8087
 *   /admin/**             -> агрегация из нескольких сервисов
 *
 * На каждом маршруте: валидация JWT -> проставить X-User-Id / X-User-Roles / X-Client-Type,
 * rate limit по userId (Redis), circuit breaker (Resilience4j).
 */
@Configuration
public class GatewayRoutes {

    @Bean
    public RouterFunction<ServerResponse> health() {
        return route(GET("/"), req -> ServerResponse.ok().bodyValue("green-eco-mall api-gateway"));
    }
}
