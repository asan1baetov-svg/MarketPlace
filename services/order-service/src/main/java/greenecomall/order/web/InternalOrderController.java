package greenecomall.order.web;

import greenecomall.order.domain.Order;
import greenecomall.order.domain.Suborder;
import greenecomall.order.order.OrderService;
import greenecomall.order.order.SuborderWorkflow;
import greenecomall.order.web.dto.InternalSuborderStatusRequest;
import greenecomall.order.web.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Служебное API для других сервисов (finance/courier/mlm). Вызывается по сети кластера;
 * {@code X-Internal-Token}, как в auth-service, подключается отдельным заданием — пока открыто.
 */
@RestController
@RequestMapping("/internal")
public class InternalOrderController {

    private final OrderService orderService;
    private final SuborderWorkflow workflow;

    public InternalOrderController(OrderService orderService, SuborderWorkflow workflow) {
        this.orderService = orderService;
        this.workflow = workflow;
    }

    @GetMapping("/orders/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        Order order = orderService.get(id);
        List<Suborder> subs = orderService.suborders(id);
        List<UUID> subIds = subs.stream().map(Suborder::getId).toList();
        return OrderResponse.of(order, subs, orderService.itemsBySuborder(subIds));
    }

    /** Для отзывов в catalog-service: отзыв оставляет только тот, кому товар доставлен. */
    @GetMapping("/purchases/received")
    public Map<String, Boolean> received(@RequestParam UUID clientUserId, @RequestParam UUID productId) {
        return Map.of("received", orderService.hasReceived(clientUserId, productId));
    }

    @PostMapping("/suborders/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setStatus(@PathVariable UUID id, @Valid @RequestBody InternalSuborderStatusRequest request) {
        workflow.transition(id, request.status(), request.actorOrDefault(), request.reason());
    }
}
