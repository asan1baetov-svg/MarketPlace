package greenecomall.catalog.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceResolution(
        UUID productId, long costPriceMinor, long salePriceMinor, BigDecimal markupPercent, String currency) {
}
