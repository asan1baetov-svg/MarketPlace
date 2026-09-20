package greenecomall.order.web;

import greenecomall.common.web.PageResponse;
import greenecomall.order.domain.Order;
import greenecomall.order.domain.OrderStatus;
import greenecomall.order.domain.Suborder;
import greenecomall.order.order.OrderService;
import greenecomall.order.web.dto.OrderResponse;
import greenecomall.order.web.dto.ReasonRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;

/** Админские эндпоинты: поиск заказов с фильтрами, отмена, возврат. */
@RestController
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/admin/orders")
    public PageResponse<OrderResponse> search(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID cityId,
            @RequestParam(required = false) UUID clientId,
            Pageable pageable) {
        Page<Order> page = orderService.adminSearch(status, cityId, clientId, pageable);
        List<OrderResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @PostMapping("/admin/orders/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest request) {
        String reason = request == null ? "cancelled by admin" : request.reasonOrDefault("cancelled by admin");
        orderService.cancel(id, reason, "admin");
    }

    @PostMapping("/admin/orders/{id}/refund")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refund(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest request) {
        String reason = request == null ? "refunded by admin" : request.reasonOrDefault("refunded by admin");
        orderService.refund(id, reason, "admin");
    }

    private OrderResponse toResponse(Order order) {
        List<Suborder> subs = orderService.suborders(order.getId());
        List<UUID> subIds = subs.stream().map(Suborder::getId).toList();
        return OrderResponse.of(order, subs, orderService.itemsBySuborder(subIds));
    }
}
