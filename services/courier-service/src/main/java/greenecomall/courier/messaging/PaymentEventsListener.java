package greenecomall.courier.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.PaymentEvents;
import greenecomall.courier.dispatch.DispatchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Консюмер топика {@code payments}: {@code OrderPaid} → запустить подбор курьеров по suborders заказа. */
@Component
public class PaymentEventsListener {

    private static final String CONSUMER = "courier-service";

    private final DispatchService dispatch;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public PaymentEventsListener(DispatchService dispatch, ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.dispatch = dispatch;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payments", groupId = CONSUMER)
    public void onMessage(String raw) {
        InboundEvent event = InboundEvent.parse(raw, objectMapper);
        if (processedEvents.alreadyHandled(event.eventId(), CONSUMER)) {
            return;
        }
        if (EventTypes.ORDER_PAID.equals(event.eventType())) {
            dispatch.onOrderPaid(event.payload(PaymentEvents.OrderPaid.class).orderId());
        }
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }
}
