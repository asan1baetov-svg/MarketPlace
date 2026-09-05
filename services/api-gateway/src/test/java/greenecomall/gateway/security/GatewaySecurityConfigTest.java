package greenecomall.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Проверяет только правила доступа (без реальной маршрутизации, которая пока не подключена —
 * см. TODO в pom.xml): публичные пути проходят без токена, остальные требуют валидный JWT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityConfigTest {

    @Autowired
    private WebTestClient client;

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
    void publicAuthPaths_bypassAuthentication() {
        // permitAll — не 401; 404 ожидаем, потому что реальной маршрутизации на auth-service ещё нет
        client.post().uri("/api/auth/login").exchange().expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
        client.post().uri("/api/auth/register").exchange().expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
        client.post().uri("/api/auth/sso/mlm").exchange().expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publicCatalogBrowsing_bypassesAuthentication() {
        client.get().uri("/api/catalog/products").exchange().expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }
}
