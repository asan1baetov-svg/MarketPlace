package greenecomall.order.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.PageResponse;
import greenecomall.common.web.security.Authz;
import greenecomall.order.domain.Order;
import greenecomall.order.domain.Suborder;
import greenecomall.order.order.OrderService;
import greenecomall.order.web.dto.CheckoutRequest;
import greenecomall.order.web.dto.CheckoutResponse;
import greenecomall.order.web.dto.ClientOrderResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Клиентские эндпоинты заказа: checkout, список своих заказов, карточка заказа. Клиент — из access-JWT. */
@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/cart/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request, AuthPrincipal principal) {
        return CheckoutResponse.from(orderService.checkout(
                Authz.require(principal).userId(), request.deliveryAddress(), request.promoCode()));
    }

    @GetMapping("/orders")
    public PageResponse<ClientOrderResponse> list(AuthPrincipal principal, Pageable pageable) {
        Page<Order> page = orderService.listForClient(Authz.require(principal).userId(), pageable);
        List<ClientOrderResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @GetMapping("/orders/{id}")
    public ClientOrderResponse get(@PathVariable UUID id, AuthPrincipal principal) {
        Order order = orderService.get(id);
        Authz.requireSelfOrAdmin(principal, order.getClientUserId());
        return toResponse(order);
    }

    private ClientOrderResponse toResponse(Order order) {
        List<Suborder> subs = orderService.suborders(order.getId());
        List<UUID> subIds = subs.stream().map(Suborder::getId).toList();
        return ClientOrderResponse.of(order, subs, orderService.itemsBySuborder(subIds));
    }
}
