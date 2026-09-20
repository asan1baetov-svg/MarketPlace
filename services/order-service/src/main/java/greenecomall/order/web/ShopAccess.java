package greenecomall.order.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.security.Authz;
import greenecomall.order.catalog.CatalogClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Проверка «магазин {@code shopId} принадлежит пользователю из токена». В access-JWT есть только
 * {@code sub}, а владельца магазина знает catalog-service — спрашиваем его служебное API.
 */
@Component
public class ShopAccess {

    private final CatalogClient catalog;

    public ShopAccess(CatalogClient catalog) {
        this.catalog = catalog;
    }

    /** Возвращает {@code shopId}, если вызывающий — его владелец или админ; иначе 403. */
    public UUID requireOwnShop(AuthPrincipal principal, UUID shopId) {
        Authz.require(principal);
        if (Authz.isAdmin(principal)) {
            return shopId;
        }
        UUID owner = catalog.shop(shopId).ownerUserId();
        if (!principal.userId().equals(owner)) {
            throw new AccessDeniedException("shop belongs to another user");
        }
        return shopId;
    }
}
