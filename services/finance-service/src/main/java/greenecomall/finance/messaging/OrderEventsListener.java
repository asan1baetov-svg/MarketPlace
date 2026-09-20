package greenecomall.finance.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.OrderEvents;
import greenecomall.finance.payment.PaymentService;
import greenecomall.finance.wallet.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Консюмер топика {@code orders}: {@code OrderCreated} → снять план распределения + создать
 * платёжное намерение; {@code OrderCancelled} → закрыть неоплаченный платёж или вернуть оплаченный
 * клиенту (с откатом распределения по кошелькам).
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);
    private static final String CONSUMER = "finance-service";

    private final SettlementService settlementService;
    private final PaymentService paymentService;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public OrderEventsListener(SettlementService settlementService, PaymentService paymentService,
                               ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.settlementService = settlementService;
        this.paymentService = paymentService;
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
            case EventTypes.ORDER_CREATED -> {
                OrderEvents.OrderCreated p = event.payload(OrderEvents.OrderCreated.class);
                settlementService.capturePlan(p);
                paymentService.createOrderPayment(p.orderId(), p.clientUserId(), p.totalAmountMinor(), p.currency());
            }
            case EventTypes.ORDER_CANCELLED -> {
                OrderEvents.OrderCancelled p = event.payload(OrderEvents.OrderCancelled.class);
                paymentService.onOrderCancelled(p.orderId(), p.reason(), event.eventId());
            }
            default -> log.debug("orders: ignoring {}", event.eventType());
        }
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }
}
