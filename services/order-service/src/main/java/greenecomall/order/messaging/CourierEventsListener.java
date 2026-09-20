package greenecomall.order.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.CourierEvents;
import greenecomall.order.domain.SuborderStatus;
import greenecomall.order.order.OrderService;
import greenecomall.order.order.SuborderWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Консюмер топика {@code couriers}: назначение курьера и статусы доставки двигают конечный
 * автомат suborder (см. docs/ARCHITECTURE.md §2.3, §4.2).
 */
@Component
public class CourierEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CourierEventsListener.class);
    private static final String CONSUMER = "order-service";

    private final OrderService orderService;
    private final SuborderWorkflow workflow;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public CourierEventsListener(OrderService orderService, SuborderWorkflow workflow,
                                 ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.workflow = workflow;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "couriers", groupId = CONSUMER)
    public void onMessage(String raw) {
        InboundEvent event = InboundEvent.parse(raw, objectMapper);
        if (processedEvents.alreadyHandled(event.eventId(), CONSUMER)) {
            return;
        }
        switch (event.eventType()) {
            case EventTypes.COURIER_ASSIGNED -> {
                CourierEvents.CourierAssigned p = event.payload(CourierEvents.CourierAssigned.class);
                orderService.assignCourier(p.suborderId(), p.courierId());
            }
            case EventTypes.DELIVERY_PICKED_UP -> move(event, SuborderStatus.HANDED_TO_COURIER);
            case EventTypes.DELIVERY_IN_TRANSIT -> move(event, SuborderStatus.IN_TRANSIT);
            case EventTypes.DELIVERY_COMPLETED -> {
                CourierEvents.DeliveryStatusChanged p = event.payload(CourierEvents.DeliveryStatusChanged.class);
                workflow.transition(p.suborderId(), SuborderStatus.DELIVERED, "couriers", null);
                workflow.transition(p.suborderId(), SuborderStatus.COMPLETED, "couriers", null);
            }
            case EventTypes.DELIVERY_FAILED -> {
                CourierEvents.DeliveryStatusChanged p = event.payload(CourierEvents.DeliveryStatusChanged.class);
                workflow.transition(p.suborderId(), SuborderStatus.CANCELLED, "couriers", p.failureReason());
            }
            default -> log.debug("couriers: ignoring {}", event.eventType());
        }
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }

    private void move(InboundEvent event, SuborderStatus target) {
        CourierEvents.DeliveryStatusChanged p = event.payload(CourierEvents.DeliveryStatusChanged.class);
        workflow.transition(p.suborderId(), target, "couriers", null);
    }
}
