package greenecomall.order.web.dto;

import greenecomall.order.domain.Order;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Заказ глазами покупателя: только цены продажи. Себестоимость магазина, наценка и комиссия
 * платформы — коммерческая тайна, они есть только в {@link OrderResponse} для админки.
 */
public record ClientOrderResponse(
        UUID id, UUID cityId, String status,
        long itemsAmountMinor, long discountAmountMinor, long totalAmountMinor, String currency,
        UUID paymentId, Instant createdAt, List<Parcel> parcels) {

    /** Посылка от одного магазина (suborder): у каждой свой статус доставки. */
    public record Parcel(UUID id, UUID shopId, String status, long amountMinor, List<Item> items) {
    }

    public record Item(UUID productId, String name, int qty, long unitPriceMinor, long lineTotalMinor) {
        static Item from(SuborderItem i) {
            return new Item(i.getProductId(), i.getNameSnapshot(), i.getQty(), i.getSalePriceMinor(),
                    i.getSalePriceMinor() * i.getQty());
        }
    }

    public static ClientOrderResponse of(Order o, List<Suborder> subs, Map<UUID, List<SuborderItem>> itemsBySuborder) {
        List<Parcel> parcels = subs.stream()
                .map(s -> new Parcel(s.getId(), s.getShopId(), s.getStatus().name(), s.getGoodsAmountMinor(),
                        itemsBySuborder.getOrDefault(s.getId(), List.of()).stream().map(Item::from).toList()))
                .toList();
        return new ClientOrderResponse(o.getId(), o.getCityId(), o.getStatus().name(), o.getItemsAmountMinor(),
                o.getDiscountAmountMinor(), o.getTotalAmountMinor(), o.getCurrency(), o.getPaymentId(),
                o.getCreatedAt(), parcels);
    }
}
