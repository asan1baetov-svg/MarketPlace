package greenecomall.order.web.dto;

import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Вид suborder для магазина: <b>только закупочная цена</b>, без наценки и цены продажи
 * (см. docs/ARCHITECTURE.md §2.3).
 */
public record ShopSuborderResponse(
        UUID id, UUID orderId, String status, long payoutAmountMinor, String currency,
        UUID courierId, Instant createdAt, List<Item> items) {

    public record Item(UUID productId, String name, int qty, long costPriceMinor) {
        static Item from(SuborderItem i) {
            return new Item(i.getProductId(), i.getNameSnapshot(), i.getQty(), i.getCostPriceMinor());
        }
    }

    public static ShopSuborderResponse of(Suborder s, String currency, List<SuborderItem> items) {
        return new ShopSuborderResponse(
                s.getId(), s.getOrderId(), s.getStatus().name(), s.getCostAmountMinor(), currency,
                s.getCourierId(), s.getCreatedAt(), items.stream().map(Item::from).toList());
    }
}
