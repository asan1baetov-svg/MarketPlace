package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.Shop;

import java.util.UUID;

/** Минимальный служебный вид магазина: другим сервисам нужен владелец для проверки доступа. */
public record InternalShopResponse(UUID id, UUID ownerUserId, String status, String name, String address,
                                   String phone, UUID cityId) {

    public static InternalShopResponse from(Shop shop) {
        return new InternalShopResponse(shop.getId(), shop.getOwnerUserId(), shop.getStatus().name(),
                shop.getName(), shop.getAddress(), shop.getPhone(), shop.getCity().getId());
    }
}
