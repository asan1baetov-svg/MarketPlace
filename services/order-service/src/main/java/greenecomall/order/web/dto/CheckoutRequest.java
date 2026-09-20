package greenecomall.order.web.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * Клиент — пользователь из access-JWT.
 *
 * @param deliveryAddress произвольный JSON адреса доставки (сохраняется как есть в {@code orders.delivery_address})
 * @param promoCode необязательный промокод
 */
public record CheckoutRequest(
        @NotNull JsonNode deliveryAddress,
        String promoCode) {
}
