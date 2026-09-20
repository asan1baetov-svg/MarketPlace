package greenecomall.order.web.dto;

import greenecomall.order.order.OrderService;

import java.util.UUID;

public record CheckoutResponse(UUID orderId, long totalAmountMinor, String currency) {

    public static CheckoutResponse from(OrderService.CheckoutResult r) {
        return new CheckoutResponse(r.orderId(), r.totalAmountMinor(), r.currency());
    }
}
