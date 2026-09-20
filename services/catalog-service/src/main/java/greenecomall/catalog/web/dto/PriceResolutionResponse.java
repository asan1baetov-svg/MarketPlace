package greenecomall.catalog.web.dto;

import greenecomall.catalog.pricing.PriceResolution;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceResolutionResponse(
        UUID productId, long costPriceMinor, long salePriceMinor, BigDecimal markupPercent, String currency) {

    public static PriceResolutionResponse from(PriceResolution resolution) {
        return new PriceResolutionResponse(
                resolution.productId(), resolution.costPriceMinor(), resolution.salePriceMinor(),
                resolution.markupPercent(), resolution.currency());
    }
}
