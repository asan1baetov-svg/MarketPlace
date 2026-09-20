package greenecomall.finance.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.CatalogEvents;
import greenecomall.finance.config.FinanceProperties;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Консюмер топика {@code catalog}: {@code ShopApproved} → завести кошелёк магазина
 * (docs/ARCHITECTURE.md §4.2 — consumer finance «создать wallet»).
 */
@Component
public class CatalogEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventsListener.class);
    private static final String CONSUMER = "finance-service";

    private final WalletService walletService;
    private final FinanceProperties properties;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public CatalogEventsListener(WalletService walletService, FinanceProperties properties,
                                 ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.walletService = walletService;
        this.properties = properties;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "catalog", groupId = CONSUMER)
    public void onMessage(String raw) {
        InboundEvent event = InboundEvent.parse(raw, objectMapper);
        if (processedEvents.alreadyHandled(event.eventId(), CONSUMER)) {
            return;
        }
        if (EventTypes.SHOP_APPROVED.equals(event.eventType())) {
            CatalogEvents.ShopApproved p = event.payload(CatalogEvents.ShopApproved.class);
            walletService.registerOwner(WalletOwnerType.SHOP, p.shopId().toString(), p.ownerUserId());
            walletService.getOrCreate(WalletOwnerType.SHOP, p.shopId().toString(), properties.defaultCurrency());
        } else {
            log.debug("catalog: ignoring {}", event.eventType());
        }
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }
}
