package greenecomall.catalog.web.dto;

import greenecomall.catalog.pricing.PriceResolution;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceResponse(
        UUID productId, long costPriceMinor, long salePriceMinor, BigDecimal markupPercent, String currency) {

    public static PriceResponse from(PriceResolution r) {
        return new PriceResponse(r.productId(), r.costPriceMinor(), r.salePriceMinor(), r.markupPercent(), r.currency());
    }
}
