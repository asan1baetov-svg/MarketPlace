package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record StockUpdateRequest(@PositiveOrZero int quantity) {
}
