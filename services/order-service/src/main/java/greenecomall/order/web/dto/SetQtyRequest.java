package greenecomall.order.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** {@code qty == 0} удаляет позицию. */
public record SetQtyRequest(@PositiveOrZero int qty) {
}
