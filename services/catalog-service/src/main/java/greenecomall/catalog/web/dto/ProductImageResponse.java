package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.ProductImage;

import java.util.UUID;

/**
 * Фото товара в кабинете магазина: {@code kind} ORIGINAL (загружено магазином) или AI,
 * {@code style} — выбранный стиль кадра и {@code wish} — пожелание к нему, {@code status} генерации,
 * {@code published} — видно ли покупателям, {@code sourceImageId} — из какого оригинала сделано.
 */
public record ProductImageResponse(UUID id, String url, int sort, String kind, String style, String wish,
                                   String status, boolean published, UUID sourceImageId, String error) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getUrl(), image.getSort(), image.getKind().name(),
                image.getStyle() == null ? null : image.getStyle().name(), image.getWish(),
                image.getStatus().name(), image.isPublished(), image.getSourceImageId(), image.getError());
    }
}
