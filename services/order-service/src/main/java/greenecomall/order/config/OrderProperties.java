package greenecomall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки order-service.
 *
 * @param catalogBaseUrl базовый URL catalog-service для служебных вызовов (резолв цены, резерв остатков)
 * @param outboxPollInterval период опроса таблицы outbox публикатором
 * @param reservationTimeout сколько ждать оплату, прежде чем отменить заказ и снять резерв остатков
 */
@ConfigurationProperties(prefix = "order")
public record OrderProperties(
        String catalogBaseUrl,
        Duration outboxPollInterval,
        Duration reservationTimeout) {

    public OrderProperties {
        if (catalogBaseUrl == null || catalogBaseUrl.isBlank()) {
            catalogBaseUrl = "http://localhost:8082";
        }
        if (outboxPollInterval == null) {
            outboxPollInterval = Duration.ofSeconds(2);
        }
        if (reservationTimeout == null) {
            reservationTimeout = Duration.ofMinutes(15);
        }
    }
}
