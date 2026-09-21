package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.Shop;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopResponse(
        UUID id, UUID ownerUserId, String name, String legalInfo, String address, String phone,
        UUID countryId, UUID cityId, String status, String rejectionReason, BigDecimal rating, int reviewsCount) {

    public static ShopResponse from(Shop shop) {
        return new ShopResponse(
                shop.getId(), shop.getOwnerUserId(), shop.getName(), shop.getLegalInfo(), shop.getAddress(),
                shop.getPhone(), shop.getCountry().getId(), shop.getCity().getId(), shop.getStatus().name(),
                shop.getRejectionReason(), shop.getRating(), shop.getReviewsCount());
    }
}
