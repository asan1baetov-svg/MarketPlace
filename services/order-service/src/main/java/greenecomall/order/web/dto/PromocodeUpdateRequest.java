package greenecomall.order.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record PromocodeUpdateRequest(
        @NotNull @Positive BigDecimal value,
        @NotNull Instant validFrom,
        @NotNull Instant validTo,
        Integer usageLimit,
        boolean active) {
}
