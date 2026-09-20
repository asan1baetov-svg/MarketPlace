package greenecomall.order.web.dto;

import greenecomall.order.domain.PromocodeFundedBy;
import greenecomall.order.domain.PromocodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record PromocodeCreateRequest(
        @NotBlank String code,
        @NotNull PromocodeType type,
        @NotNull @Positive BigDecimal value,
        @NotNull PromocodeFundedBy fundedBy,
        @NotNull Instant validFrom,
        @NotNull Instant validTo,
        Integer usageLimit) {
}
