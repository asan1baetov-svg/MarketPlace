package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.Shop;

import java.math.BigDecimal;
import java.util.UUID;

/** Публичная карточка магазина: без владельца, реквизитов и причины отказа модерации. */
public record PublicShopResponse(UUID id, String name, UUID countryId, UUID cityId,
                                 BigDecimal rating, int reviewsCount) {

    public static PublicShopResponse from(Shop shop) {
        return new PublicShopResponse(shop.getId(), shop.getName(), shop.getCountry().getId(),
                shop.getCity().getId(), shop.getRating(), shop.getReviewsCount());
    }
}
