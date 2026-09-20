package greenecomall.order.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Владелец корзины — пользователь из access-JWT.
 *
 * @param cityId текущий город клиента; фиксирует город корзины при первом товаре
 */
public record AddCartItemRequest(
        @NotNull UUID productId,
        @NotNull UUID cityId,
        @Positive int qty) {
}
