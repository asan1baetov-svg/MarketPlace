package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record StockSetRequest(@PositiveOrZero int quantity) {
}
