package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MarkupRuleUpdateRequest(@NotNull BigDecimal markupPercent, int priority, boolean active) {
}
