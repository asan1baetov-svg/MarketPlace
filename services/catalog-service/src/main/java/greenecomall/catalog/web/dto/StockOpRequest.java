package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record StockOpRequest(@NotNull UUID productId, @Positive int qty) {
}
