package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.Shop;

import java.util.UUID;

/** Минимальный служебный вид магазина: другим сервисам нужен владелец для проверки доступа. */
public record InternalShopResponse(UUID id, UUID ownerUserId, String status) {

    public static InternalShopResponse from(Shop shop) {
        return new InternalShopResponse(shop.getId(), shop.getOwnerUserId(), shop.getStatus().name());
    }
}
