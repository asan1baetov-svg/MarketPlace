package greenecomall.mlm.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.AuthEvents;
import greenecomall.common.events.payload.OrderEvents;
import greenecomall.common.events.payload.PaymentEvents;
import greenecomall.mlm.core.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Консюмер {@code auth} (SSO-связка → MLM-аккаунт), {@code orders} (снимок заказа / отмена) и
 * {@code payments} (оплата заказа → зачёт, возврат → откат, оплата доступа → старт окна).
 */
@Component
public class MlmEventsListener {

    private static final Logger log = LoggerFactory.getLogger(MlmEventsListener.class);
    private static final String CONSUMER = "mlm-service";

    private final AccountService accounts;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public MlmEventsListener(AccountService accounts, ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"auth", "orders", "payments"}, groupId = CONSUMER)
    @Transactional
    public void onMessage(String raw) {
        InboundEvent event = InboundEvent.parse(raw, objectMapper);
        if (processedEvents.alreadyHandled(event.eventId(), CONSUMER)) {
            return;
        }
        switch (event.eventType()) {
            case EventTypes.MLM_USER_LINKED -> accounts.link(event.payload(AuthEvents.MlmUserLinked.class));
            case EventTypes.ORDER_CREATED -> accounts.onOrderCreated(event.payload(OrderEvents.OrderCreated.class));
            case EventTypes.ORDER_CANCELLED ->
                    accounts.onOrderReversed(event.payload(OrderEvents.OrderCancelled.class).orderId());
            case EventTypes.ORDER_PAID -> {
                PaymentEvents.OrderPaid p = event.payload(PaymentEvents.OrderPaid.class);
                accounts.onOrderPaid(p.orderId(), p.paidAt());
            }
            case EventTypes.PAYMENT_REFUNDED ->
                    accounts.onOrderReversed(event.payload(PaymentEvents.PaymentRefunded.class).orderId());
            case EventTypes.MLM_ACCESS_PAID -> {
                PaymentEvents.MlmAccessPaid p = event.payload(PaymentEvents.MlmAccessPaid.class);
                accounts.onAccessPaid(p.mlmUserId(), p.tariffId(), p.amountMinor(), p.currency(), p.paidAt());
            }
            default -> log.trace("ignoring {}", event.eventType());
        }
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }
}
