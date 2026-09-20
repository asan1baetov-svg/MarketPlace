package greenecomall.order.web;

import greenecomall.common.security.Roles;
import greenecomall.common.test.TestJwts;
import greenecomall.common.test.TestSecurityConfig;
import greenecomall.order.catalog.CatalogClient;
import greenecomall.order.web.dto.CartResponse;
import greenecomall.order.web.dto.CheckoutResponse;
import greenecomall.order.web.dto.ClientOrderResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Полный путь checkout через настоящие HTTP-контроллеры и настоящий Postgres (Testcontainers).
 * catalog-service подменён моком {@link CatalogClient}: тест проверяет логику order-service,
 * а не сетевой контракт.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class CheckoutFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @MockitoBean
    private CatalogClient catalog;

    @Test
    void multiVendorCheckout_createsOrderWithSuborderPerShop() {
        UUID client = UUID.randomUUID();
        UUID city = UUID.randomUUID();
        UUID shopA = UUID.randomUUID();
        UUID shopB = UUID.randomUUID();
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();

        Mockito.when(catalog.storefrontProduct(eq(productA), any()))
                .thenReturn(new CatalogClient.StorefrontProduct(productA, shopA, "Apple", 12_000, "KGS"));
        Mockito.when(catalog.storefrontProduct(eq(productB), any()))
                .thenReturn(new CatalogClient.StorefrontProduct(productB, shopB, "Bread", 5_000, "KGS"));
        Mockito.when(catalog.price(eq(productA), any()))
                .thenReturn(new CatalogClient.PriceView(productA, 10_000, 12_000, new BigDecimal("20.00"), "KGS"));
        Mockito.when(catalog.price(eq(productB), any()))
                .thenReturn(new CatalogClient.PriceView(productB, 4_000, 5_000, new BigDecimal("25.00"), "KGS"));

        assertThat(rest.postForEntity("/cart/items",
                Map.of("productId", productA, "cityId", city, "qty", 2), String.class).getStatusCode())
                .as("без access-JWT корзина недоступна").isEqualTo(HttpStatus.UNAUTHORIZED);

        rest.exchange("/cart/items", HttpMethod.POST,
                as(client, Map.of("productId", productA, "cityId", city, "qty", 2)), CartResponse.class);
        ResponseEntity<CartResponse> cart = rest.exchange("/cart/items", HttpMethod.POST,
                as(client, Map.of("productId", productB, "cityId", city, "qty", 1)), CartResponse.class);
        assertThat(cart.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cart.getBody().items()).hasSize(2);
        assertThat(cart.getBody().subtotalMinor()).isEqualTo(2 * 12_000 + 5_000);

        ResponseEntity<CheckoutResponse> checkout = rest.exchange("/cart/checkout", HttpMethod.POST,
                as(client, Map.of("deliveryAddress", Map.of("street", "Chuy 1"))), CheckoutResponse.class);
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkout.getBody().totalAmountMinor()).isEqualTo(29_000);

        Mockito.verify(catalog).reserve(productA, 2);
        Mockito.verify(catalog).reserve(productB, 1);

        UUID orderId = checkout.getBody().orderId();
        ResponseEntity<ClientOrderResponse> order = rest.exchange("/orders/" + orderId, HttpMethod.GET,
                as(client, null), ClientOrderResponse.class);
        assertThat(order.getBody().status()).isEqualTo("CREATED");
        assertThat(order.getBody().parcels()).hasSize(2);
        assertThat(order.getBody().parcels())
                .allSatisfy(p -> assertThat(p.items()).isNotEmpty());
        // покупатель не видит себестоимость и наценку
        assertThat(rest.exchange("/orders/" + orderId, HttpMethod.GET, as(client, null), String.class).getBody())
                .doesNotContain("costPriceMinor", "markupPercent", "platformCommission");

        // корзина очищена
        ResponseEntity<CartResponse> emptied = rest.exchange("/cart", HttpMethod.GET, as(client, null), CartResponse.class);
        assertThat(emptied.getBody().items()).isEmpty();

        // чужой заказ не виден
        assertThat(rest.exchange("/orders/" + orderId, HttpMethod.GET, as(UUID.randomUUID(), null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static <T> HttpEntity<T> as(UUID userId, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, TestJwts.bearer(userId, Roles.CLIENT_EXTERNAL));
        return new HttpEntity<>(body, headers);
    }
}
