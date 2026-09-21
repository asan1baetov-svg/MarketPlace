package greenecomall.order.web.dto;

import greenecomall.order.domain.Order;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderItem;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Посылка глазами доставки (служебное API для courier-service): куда везти, что внутри и на какую
 * сумму. Себестоимости и наценки здесь нет — курьеру они не нужны.
 */
public record SuborderDeliveryResponse(
        UUID suborderId, UUID orderId, UUID shopId, UUID cityId, String status,
        JsonNode deliveryAddress, long amountMinor, String currency, String orderStatus, List<Item> items) {

    public record Item(String name, int qty) {
    }

    public static SuborderDeliveryResponse of(Suborder suborder, Order order, List<SuborderItem> items,
                                              JsonNode deliveryAddress) {
        return new SuborderDeliveryResponse(suborder.getId(), order.getId(), suborder.getShopId(), order.getCityId(),
                suborder.getStatus().name(), deliveryAddress, suborder.getGoodsAmountMinor(), order.getCurrency(),
                order.getStatus().name(),
                items.stream().map(i -> new Item(i.getNameSnapshot(), i.getQty())).toList());
    }
}
