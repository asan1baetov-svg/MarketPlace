package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record InternalStockRequest(@NotNull UUID productId, @Positive int quantity) {
}
