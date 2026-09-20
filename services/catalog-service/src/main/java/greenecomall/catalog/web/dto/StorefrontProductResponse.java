package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.Product;
import greenecomall.catalog.pricing.PriceResolution;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Публичный вид товара — {@code salePriceMinor} вместо {@code costPriceMinor}, наценка не раскрывается. */
public record StorefrontProductResponse(
        UUID id, UUID shopId, UUID categoryId, String name, String description, String unit,
        long salePriceMinor, String currency, BigDecimal rating, int reviewsCount,
        String shopName, List<String> images) {

    /** @param images URL фото по порядку; в списке витрины — все фото товара, первое — обложка */
    public static StorefrontProductResponse from(Product product, PriceResolution price, List<String> images) {
        return new StorefrontProductResponse(
                product.getId(), product.getShop().getId(), product.getCategory().getId(), product.getName(),
                product.getDescription(), product.getUnit().name(), price.salePriceMinor(), price.currency(),
                product.getRating(), product.getReviewsCount(), product.getShop().getName(), images);
    }
}
