package greenecomall.gateway.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Правила доступа и проксирование: все сервисы указывают на локальный echo-upstream, который
 * возвращает метод, путь и полученный {@code X-User-Id} — так видно, куда и с чем ушёл запрос.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityConfigTest {

    private static final DisposableServer UPSTREAM = HttpServer.create()
            .port(0)
            .route(routes -> routes.route(req -> true, (req, res) -> res
                    .header("X-Echo-User", String.valueOf(req.requestHeaders().get("X-User-Id")))
                    .header("X-Echo-Internal", String.valueOf(req.requestHeaders().get("X-Internal-Token")))
                    .sendString(Mono.just(req.method().name() + " " + req.uri()))))
            .bindNow();

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + UPSTREAM.port();
        for (String service : new String[]{"auth", "catalog", "order", "finance", "courier", "mlm", "notification"}) {
            registry.add("gateway.services." + service, () -> url);
        }
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.disposeNow();
    }

    @Autowired
    private WebTestClient client;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    void publicRootPath_isAccessibleWithoutToken() {
        client.get().uri("/").exchange().expectStatus().isOk();
    }

    @Test
    void actuatorHealth_isAccessibleWithoutToken() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void protectedPath_withoutToken_isRejected() {
        client.get().uri("/api/orders/123").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedPath_withGarbageToken_isRejected() {
        client.get().uri("/api/orders/123")
                .header("Authorization", "Bearer not-a-real-jwt")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminPath_withoutToken_isRejected() {
        client.get().uri("/api/admin/orders").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicAuthPath_isProxiedWithoutApiPrefix() {
        client.post().uri("/api/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("POST /auth/login");
        client.post().uri("/api/auth/sso/mlm").exchange()
                .expectBody(String.class).isEqualTo("POST /auth/sso/mlm");
    }

    @Test
    void publicCatalogBrowsing_keepsQueryString() {
        client.get().uri("/api/catalog/products?cityId=42&page=1").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("GET /catalog/products?cityId=42&page=1");
    }

    @Test
    void webhooks_arePublicAndRoutedToOwningService() {
        client.post().uri("/api/webhooks/acquiring/mock").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("POST /webhooks/acquiring/mock");
    }

    @Test
    void spoofedIdentityHeaderFromClient_isNotForwarded() {
        client.get().uri("/api/catalog/products")
                .header("X-User-Id", "attacker")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Echo-User", "null");
    }

    @Test
    void pathTraversalToInternalApi_isNotProxied() {
        for (String path : new String[]{
                "/api/catalog/../internal/catalog/price",
                "/api/catalog/%2e%2e/internal/catalog/price",
                "/api/catalog/%2E%2E/internal/catalog/price",
                "/api/catalog/..%2finternal/catalog/price",
                "/api/catalog/;/../internal/catalog/price"}) {
            client.get().uri(java.net.URI.create("http://localhost:" + port + path)).exchange()
                    .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status)
                            .as(path).isBetween(400, 499))
                    .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(
                            body == null ? "" : body).as(path).doesNotContain("/internal/"));
        }
    }

    @Test
    void internalTokenFromClient_isNotForwarded() {
        client.get().uri("/api/catalog/products")
                .header("X-Internal-Token", "guessed")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Echo-Internal", "null");
    }

    @Test
    void adminWallets_isRoutedToFinance() {
        org.assertj.core.api.Assertions.assertThat(greenecomall.gateway.routing.RouteTable.resolve("/admin/wallets"))
                .hasValueSatisfying(r -> org.assertj.core.api.Assertions.assertThat(r.service()).isEqualTo("finance"));
    }

    @Test
    void browserPreflight_isAllowedWithoutToken() {
        client.options().uri("/api/auth/login")
                .header("Origin", "https://shop.greenecomall.kg")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://shop.greenecomall.kg");
    }

    @Test
    void crossOriginResponse_carriesCorsHeader() {
        client.get().uri("/api/catalog/products?cityId=1")
                .header("Origin", "https://shop.greenecomall.kg")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://shop.greenecomall.kg");
    }
}
