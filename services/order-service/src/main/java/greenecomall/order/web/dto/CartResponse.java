package greenecomall.order.web.dto;

import greenecomall.order.cart.CartService;
import greenecomall.order.domain.CartItem;

import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID cartId, UUID cityId, List<Item> items, long subtotalMinor, String currency) {

    public record Item(UUID id, UUID productId, UUID shopId, String productName, int qty,
                       long unitSalePriceMinor, long lineTotalMinor, String currency) {
        static Item from(CartItem i) {
            return new Item(i.getId(), i.getProductId(), i.getShopId(), i.getProductName(), i.getQty(),
                    i.getUnitSalePriceMinor(), i.getUnitSalePriceMinor() * i.getQty(), i.getCurrency());
        }
    }

    public static CartResponse from(CartService.CartView view) {
        List<Item> items = view.items().stream().map(Item::from).toList();
        String currency = items.isEmpty() ? null : items.get(0).currency();
        return new CartResponse(view.cartId(), view.cityId(), items, view.subtotalMinor(), currency);
    }
}
