package greenecomall.finance.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.CourierEvents;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.domain.WalletTransaction;
import greenecomall.finance.wallet.WalletService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Консюмер топика {@code couriers}: {@code CourierRegistered} → владелец кошельков курьера;
 * {@code DeliveryCompleted} с {@code feeMinor} → зачисление вознаграждения на кошелёк курьера
 * (сдельная оплата от платформы, docs/ARCHITECTURE.md §2.4).
 */
@Component
public class CourierEventsListener {

    private static final String CONSUMER = "finance-service";

    private final WalletService walletService;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public CourierEventsListener(WalletService walletService, ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.walletService = walletService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "couriers", groupId = CONSUMER)
    public void onMessage(String raw) {
        InboundEvent event = InboundEvent.parse(raw, objectMapper);
        if (processedEvents.alreadyHandled(event.eventId(), CONSUMER)) {
            return;
        }
        if (EventTypes.COURIER_REGISTERED.equals(event.eventType())) {
            CourierEvents.CourierRegistered p = event.payload(CourierEvents.CourierRegistered.class);
            walletService.registerOwner(WalletOwnerType.COURIER, p.courierId().toString(), p.userId());
        } else if (EventTypes.DELIVERY_COMPLETED.equals(event.eventType())) {
            CourierEvents.DeliveryStatusChanged p = event.payload(CourierEvents.DeliveryStatusChanged.class);
            if (p.feeMinor() != null && p.feeMinor() > 0 && p.courierId() != null) {
                walletService.credit(WalletOwnerType.COURIER, p.courierId().toString(), p.currency(), p.feeMinor(),
                        WalletTransaction.Type.COURIER_FEE, "suborder", p.suborderId().toString(),
                        "courier-fee:" + p.suborderId());
            }
        }
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }
}
