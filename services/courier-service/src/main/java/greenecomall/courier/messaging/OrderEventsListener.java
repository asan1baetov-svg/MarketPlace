package greenecomall.courier.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.OrderEvents;
import greenecomall.courier.dispatch.DispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Консюмер топика {@code orders}: {@code OrderCreated} → завести задачи доставки,
 * {@code SuborderStatusChanged → ASSEMBLED} → можно забирать, {@code OrderCancelled} → снять задачи.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);
    private static final String CONSUMER = "courier-service";

    private final DispatchService dispatch;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public OrderEventsListener(DispatchService dispatch, ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.dispatch = dispatch;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = CONSUMER)
    public void onMessage(String raw) {
        InboundEvent event = InboundEvent.parse(raw, objectMapper);
        if (processedEvents.alreadyHandled(event.eventId(), CONSUMER)) {
            return;
        }
        switch (event.eventType()) {
            case EventTypes.ORDER_CREATED -> dispatch.registerJobs(event.payload(OrderEvents.OrderCreated.class));
            case EventTypes.SUBORDER_STATUS_CHANGED -> {
                OrderEvents.SuborderStatusChanged p = event.payload(OrderEvents.SuborderStatusChanged.class);
                if ("ASSEMBLED".equals(p.toStatus())) {
                    dispatch.onSuborderAssembled(p.suborderId());
                }
            }
            case EventTypes.ORDER_CANCELLED ->
                    dispatch.onOrderCancelled(event.payload(OrderEvents.OrderCancelled.class).orderId());
            default -> log.debug("orders: ignoring {}", event.eventType());
        }
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }
}
