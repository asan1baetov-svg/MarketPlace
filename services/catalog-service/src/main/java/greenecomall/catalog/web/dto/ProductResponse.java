package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.Product;

import java.math.BigDecimal;
import java.util.UUID;

/** Вид товара для владельца магазина/админа — включает {@code costPriceMinor}, не показывается витрине. */
public record ProductResponse(
        UUID id, UUID shopId, UUID categoryId, String name, String description, String unit,
        long costPriceMinor, String currency, String status, String rejectionReason,
        UUID cityId, BigDecimal rating, int reviewsCount) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(), product.getShop().getId(), product.getCategory().getId(), product.getName(),
                product.getDescription(), product.getUnit().name(), product.getCostPriceMinor(),
                product.getCurrency(), product.getStatus().name(), product.getRejectionReason(),
                product.getCity().getId(), product.getRating(), product.getReviewsCount());
    }
}
