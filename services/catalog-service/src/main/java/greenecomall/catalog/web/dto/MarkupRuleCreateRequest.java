package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.MarkupScope;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record MarkupRuleCreateRequest(
        @NotNull MarkupScope scope, UUID categoryId, UUID shopId, UUID productId, UUID countryId, UUID cityId,
        @NotNull BigDecimal markupPercent, int priority, boolean active) {
}
