package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.MarkupRule;

import java.math.BigDecimal;
import java.util.UUID;

public record MarkupRuleResponse(
        UUID id, String scope, UUID categoryId, UUID shopId, UUID productId, UUID countryId, UUID cityId,
        BigDecimal markupPercent, int priority, boolean active) {

    public static MarkupRuleResponse from(MarkupRule rule) {
        return new MarkupRuleResponse(
                rule.getId(), rule.getScope().name(),
                rule.getCategory() != null ? rule.getCategory().getId() : null,
                rule.getShop() != null ? rule.getShop().getId() : null,
                rule.getProduct() != null ? rule.getProduct().getId() : null,
                rule.getCountry() != null ? rule.getCountry().getId() : null,
                rule.getCity() != null ? rule.getCity().getId() : null,
                rule.getMarkupPercent(), rule.getPriority(), rule.isActive());
    }
}
