package greenecomall.courier.delivery;

import greenecomall.common.domain.DomainException;
import greenecomall.courier.CourierErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Данные для приложения курьера: куда везти (order-service) и откуда забрать (catalog-service).
 * Внутри кластера по {@code X-Internal-Token}. Недоступность соседа не должна прятать саму доставку,
 * поэтому адрес забора необязателен — вернём, что есть.
 */
@Component
public class DeliveryDetailsClient {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDetailsClient.class);

    private final RestClient orders;
    private final RestClient catalog;

    public DeliveryDetailsClient(RestClient orderRestClient, RestClient catalogRestClient) {
        this.orders = orderRestClient;
        this.catalog = catalogRestClient;
    }

    /** Адрес клиента, состав и сумма посылки. */
    public Dropoff dropoff(UUID suborderId) {
        try {
            JsonNode body = orders.get()
                    .uri("/internal/suborders/{id}/delivery", suborderId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new DomainException(CourierErrors.ORDER_UNAVAILABLE,
                                "order-service returned HTTP " + res.getStatusCode());
                    })
                    .body(JsonNode.class);
            List<Dropoff.Item> items = body.path("items").valueStream()
                    .map(i -> new Dropoff.Item(i.path("name").asString(""), i.path("qty").asInt()))
                    .toList();
            return new Dropoff(body.path("deliveryAddress"), body.path("amountMinor").asLong(),
                    body.path("currency").asString("KGS"), body.path("orderStatus").asString(null), items);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException(CourierErrors.ORDER_UNAVAILABLE, "cannot load delivery details", e);
        }
    }

    /** Магазин, откуда забирать: название, адрес, телефон. Пусто, если catalog недоступен. */
    public Optional<Pickup> pickup(UUID shopId) {
        try {
            JsonNode body = catalog.get()
                    .uri("/internal/catalog/shops/{id}", shopId)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.of(new Pickup(body.path("name").asString(null), body.path("address").asString(null),
                    body.path("phone").asString(null)));
        } catch (RuntimeException e) {
            log.warn("cannot load pickup address for shop {}: {}", shopId, e.toString());
            return Optional.empty();
        }
    }

    public record Dropoff(JsonNode address, long amountMinor, String currency, String orderStatus, List<Item> items) {
        public record Item(String name, int qty) {
        }
    }

    public record Pickup(String shopName, String address, String phone) {
    }
}
