package greenecomall.order.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.PaymentEvents;
import greenecomall.order.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Консюмер топика {@code payments}: {@code OrderPaid} → заказ оплачен, {@code PaymentFailed} →
 * заказ не оплачен и резервы сняты. Идемпотентно по {@code eventId} (см. {@link ProcessedEvents}).
 */
@Component
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);
    private static final String CONSUMER = "order-service";

    private final OrderService orderService;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public PaymentEventsListener(OrderService orderService, ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payments", groupId = CONSUMER)
    public void onMessage(String raw) {
        InboundEvent event = InboundEvent.parse(raw, objectMapper);
        if (processedEvents.alreadyHandled(event.eventId(), CONSUMER)) {
            return;
        }
        switch (event.eventType()) {
            case EventTypes.ORDER_PAID -> {
                PaymentEvents.OrderPaid p = event.payload(PaymentEvents.OrderPaid.class);
                orderService.markPaid(p.orderId(), p.paymentId());
            }
            case EventTypes.PAYMENT_FAILED -> {
                PaymentEvents.PaymentFailed p = event.payload(PaymentEvents.PaymentFailed.class);
                orderService.markPaymentFailed(p.orderId(), p.reason());
            }
            default -> log.debug("payments: ignoring {}", event.eventType());
        }
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }
}
