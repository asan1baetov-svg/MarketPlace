package greenecomall.order.web.dto;

import greenecomall.order.domain.Order;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Полный вид заказа для админки и служебного API: содержит себестоимость, наценку и комиссию платформы. */
public record OrderResponse(
        UUID id, UUID clientUserId, UUID cityId, String status,
        long itemsAmountMinor, long discountAmountMinor, long totalAmountMinor, String currency,
        UUID paymentId, Instant createdAt, List<SuborderView> suborders) {

    public record SuborderView(
            UUID id, UUID shopId, String status, long goodsAmountMinor, long costAmountMinor,
            long platformCommissionMinor, UUID courierId, List<ItemView> items) {
    }

    public record ItemView(
            UUID productId, String name, int qty, long costPriceMinor, long salePriceMinor,
            BigDecimal markupPercentSnapshot) {
        static ItemView from(SuborderItem i) {
            return new ItemView(i.getProductId(), i.getNameSnapshot(), i.getQty(),
                    i.getCostPriceMinor(), i.getSalePriceMinor(), i.getMarkupPercentSnapshot());
        }
    }

    public static OrderResponse of(Order o, List<Suborder> subs, Map<UUID, List<SuborderItem>> itemsBySuborder) {
        List<SuborderView> views = subs.stream()
                .map(s -> new SuborderView(
                        s.getId(), s.getShopId(), s.getStatus().name(), s.getGoodsAmountMinor(),
                        s.getCostAmountMinor(), s.getPlatformCommissionMinor(), s.getCourierId(),
                        itemsBySuborder.getOrDefault(s.getId(), List.of()).stream().map(ItemView::from).toList()))
                .toList();
        return new OrderResponse(
                o.getId(), o.getClientUserId(), o.getCityId(), o.getStatus().name(),
                o.getItemsAmountMinor(), o.getDiscountAmountMinor(), o.getTotalAmountMinor(), o.getCurrency(),
                o.getPaymentId(), o.getCreatedAt(), views);
    }
}
