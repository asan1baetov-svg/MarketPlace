package greenecomall.order.order;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.OrderEvents;
import greenecomall.order.OrderErrors;
import greenecomall.order.domain.Order;
import greenecomall.order.domain.OrderStatus;
import greenecomall.order.domain.OrderStatusHistory;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderStatus;
import greenecomall.order.repo.OrderRepository;
import greenecomall.order.repo.OrderStatusHistoryRepository;
import greenecomall.order.repo.SuborderRepository;
import greenecomall.order.support.Tracing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Единственная точка перевода suborder по конечному автомату: пишет журнал, публикует
 * {@code orders.SuborderStatusChanged}, пересчитывает агрегатный статус заказа и публикует
 * {@code orders.OrderCompleted}, когда все suborders завершены.
 */
@Service
public class SuborderWorkflow {

    private static final String PRODUCER = "order-service";

    private final SuborderRepository suborders;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final DomainEventPublisher events;

    public SuborderWorkflow(SuborderRepository suborders, OrderRepository orders,
                            OrderStatusHistoryRepository history, DomainEventPublisher events) {
        this.suborders = suborders;
        this.orders = orders;
        this.history = history;
        this.events = events;
    }

    @Transactional
    public void transition(UUID suborderId, SuborderStatus target, String actor, String reason) {
        Suborder suborder = suborders.findById(suborderId)
                .orElseThrow(() -> new DomainException(OrderErrors.SUBORDER_NOT_FOUND, "suborder not found: " + suborderId));
        apply(suborder, target, actor, reason);
    }

    @Transactional
    public void transitionAllOfOrder(UUID orderId, SuborderStatus target, String actor, String reason) {
        for (Suborder suborder : suborders.findByOrderId(orderId)) {
            if (suborder.getStatus().canTransitionTo(target)) {
                apply(suborder, target, actor, reason);
            }
        }
    }

    private void apply(Suborder suborder, SuborderStatus target, String actor, String reason) {
        if (suborder.getStatus() == target) {
            return;
        }
        SuborderStatus from = suborder.transitionTo(target);
        history.save(OrderStatusHistory.forSuborder(suborder.getId(), from, target, actor, reason));
        events.publish(Topics.ORDERS, suborder.getOrderId().toString(),
                EventEnvelope.of(EventTypes.SUBORDER_STATUS_CHANGED, PRODUCER, Tracing.currentTraceId(),
                        new OrderEvents.SuborderStatusChanged(
                                suborder.getId(), suborder.getOrderId(), suborder.getShopId(),
                                from.name(), target.name())));
        recomputeOrder(suborder.getOrderId(), actor);
    }

    private void recomputeOrder(UUID orderId, String actor) {
        Order order = orders.findById(orderId).orElseThrow();
        List<Suborder> all = suborders.findByOrderId(orderId);
        OrderStatus before = order.getStatus();
        order.recomputeStatus(all);
        if (order.getStatus() != before) {
            history.save(OrderStatusHistory.forOrder(orderId, before, order.getStatus(), actor, "aggregate"));
            if (order.getStatus() == OrderStatus.COMPLETED) {
                events.publish(Topics.ORDERS, orderId.toString(),
                        EventEnvelope.of(EventTypes.ORDER_COMPLETED, PRODUCER, Tracing.currentTraceId(),
                                new OrderEvents.OrderCompleted(orderId, order.getClientUserId(),
                                        order.getTotalAmountMinor(), order.getCurrency())));
            }
        }
    }
}
