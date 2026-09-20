package greenecomall.notification.messaging;

import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.AuthEvents;
import greenecomall.common.events.payload.CatalogEvents;
import greenecomall.common.events.payload.CourierEvents;
import greenecomall.common.events.payload.MlmEvents;
import greenecomall.common.events.payload.NotificationCommands;
import greenecomall.common.events.payload.OrderEvents;
import greenecomall.common.events.payload.WalletEvents;
import greenecomall.notification.domain.Channel;
import greenecomall.notification.domain.RecipientLink.RefType;
import greenecomall.notification.send.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Подписка на все существенные события (docs/ARCHITECTURE.md §2.7) + командный топик
 * {@code notifications.commands}. Каждое событие → код шаблона + получатель (через
 * {@link RecipientDirectory}, если в событии нет userId).
 */
@Component
public class DomainEventsListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventsListener.class);
    private static final String CONSUMER = "notification-service";

    private final NotificationService notifications;
    private final RecipientDirectory directory;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    public DomainEventsListener(NotificationService notifications, RecipientDirectory directory,
                                ProcessedEvents processedEvents, ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.directory = directory;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"auth", "catalog", "orders", "couriers", "wallet", "mlm", "notifications.commands"},
            groupId = CONSUMER)
    @Transactional
    public void onMessage(String raw) {
        InboundEvent event = InboundEvent.parse(raw, objectMapper);
        if (processedEvents.alreadyHandled(event.eventId(), CONSUMER)) {
            return;
        }
        route(event);
        processedEvents.markHandled(event.eventId(), CONSUMER);
    }

    void route(InboundEvent event) {
        switch (event.eventType()) {
            // ─── auth ────────────────────────────────────────────────────────
            case EventTypes.USER_REGISTERED -> {
                AuthEvents.UserRegistered p = event.payload(AuthEvents.UserRegistered.class);
                send(p.userId(), "USER_WELCOME", Map.of());
            }
            case EventTypes.MLM_USER_LINKED -> {
                AuthEvents.MlmUserLinked p = event.payload(AuthEvents.MlmUserLinked.class);
                directory.remember(RefType.MLM_USER, p.mlmUserId(), p.userId());
            }
            // ─── catalog ─────────────────────────────────────────────────────
            case EventTypes.SHOP_APPROVED -> {
                CatalogEvents.ShopApproved p = event.payload(CatalogEvents.ShopApproved.class);
                directory.remember(RefType.SHOP, p.shopId(), p.ownerUserId());
                send(p.ownerUserId(), "SHOP_APPROVED", Map.of("shopId", p.shopId()));
            }
            case EventTypes.SHOP_SUSPENDED -> {
                CatalogEvents.ShopSuspended p = event.payload(CatalogEvents.ShopSuspended.class);
                sendTo(RefType.SHOP, p.shopId(), "SHOP_SUSPENDED", Map.of("reason", nz(p.reason())));
            }
            case EventTypes.PRODUCT_PUBLISHED -> {
                CatalogEvents.ProductPublished p = event.payload(CatalogEvents.ProductPublished.class);
                sendTo(RefType.SHOP, p.shopId(), "PRODUCT_PUBLISHED", Map.of("productId", p.productId()));
            }
            case EventTypes.PRODUCT_REJECTED -> {
                CatalogEvents.ProductRejected p = event.payload(CatalogEvents.ProductRejected.class);
                sendTo(RefType.SHOP, p.shopId(), "PRODUCT_REJECTED",
                        Map.of("productId", p.productId(), "reason", nz(p.reason())));
            }
            // ─── orders ──────────────────────────────────────────────────────
            case EventTypes.ORDER_CREATED -> {
                OrderEvents.OrderCreated p = event.payload(OrderEvents.OrderCreated.class);
                directory.remember(RefType.ORDER, p.orderId(), p.clientUserId());
                send(p.clientUserId(), "ORDER_CREATED", Map.of("orderId", p.orderId(),
                        "amount", money(p.totalAmountMinor(), p.currency())));
            }
            case EventTypes.SUBORDER_STATUS_CHANGED -> {
                OrderEvents.SuborderStatusChanged p = event.payload(OrderEvents.SuborderStatusChanged.class);
                sendTo(RefType.ORDER, p.orderId(), "ORDER_STATUS_CHANGED",
                        Map.of("orderId", p.orderId(), "status", p.toStatus()));
                if ("PAID".equals(p.toStatus())) {
                    sendTo(RefType.SHOP, p.shopId(), "SHOP_NEW_SUBORDER", Map.of("suborderId", p.suborderId()));
                }
            }
            case EventTypes.ORDER_CANCELLED -> {
                OrderEvents.OrderCancelled p = event.payload(OrderEvents.OrderCancelled.class);
                sendTo(RefType.ORDER, p.orderId(), "ORDER_CANCELLED",
                        Map.of("orderId", p.orderId(), "reason", nz(p.reason())));
            }
            case EventTypes.ORDER_COMPLETED -> {
                OrderEvents.OrderCompleted p = event.payload(OrderEvents.OrderCompleted.class);
                send(p.clientUserId(), "ORDER_COMPLETED", Map.of("orderId", p.orderId()));
            }
            // ─── couriers ────────────────────────────────────────────────────
            case EventTypes.COURIER_REGISTERED -> {
                CourierEvents.CourierRegistered p = event.payload(CourierEvents.CourierRegistered.class);
                directory.remember(RefType.COURIER, p.courierId(), p.userId());
                send(null, "ADMIN_COURIER_REGISTERED", Map.of("courierId", p.courierId(), "cityId", p.cityId()));
            }
            case EventTypes.COURIER_ASSIGNED -> {
                CourierEvents.CourierAssigned p = event.payload(CourierEvents.CourierAssigned.class);
                sendTo(RefType.COURIER, p.courierId(), "COURIER_NEW_OFFER", Map.of("suborderId", p.suborderId()));
            }
            case EventTypes.NO_COURIER_AVAILABLE -> {
                CourierEvents.NoCourierAvailable p = event.payload(CourierEvents.NoCourierAvailable.class);
                send(null, "ADMIN_NO_COURIER", Map.of("suborderId", p.suborderId(), "cityId", p.cityId()));
            }
            // ─── wallet ──────────────────────────────────────────────────────
            case EventTypes.WALLET_CREDITED -> {
                WalletEvents.WalletCredited p = event.payload(WalletEvents.WalletCredited.class);
                Optional<UUID> owner = walletOwner(p.ownerType(), p.ownerRef());
                owner.ifPresent(u -> directory.remember(RefType.WALLET, p.walletId(), u));
                owner.ifPresent(u -> send(u, "WALLET_CREDITED",
                        Map.of("amount", money(p.amountMinor(), p.currency()), "type", p.type())));
            }
            case EventTypes.PAYOUT_REQUESTED -> {
                WalletEvents.PayoutRequested p = event.payload(WalletEvents.PayoutRequested.class);
                directory.find(RefType.WALLET, p.walletId())
                        .ifPresent(u -> directory.remember(RefType.PAYOUT, p.payoutId(), u));
                send(null, "ADMIN_PAYOUT_REQUESTED",
                        Map.of("payoutId", p.payoutId(), "amount", money(p.amountMinor(), p.currency())));
            }
            case EventTypes.PAYOUT_APPROVED, EventTypes.PAYOUT_PAID -> {
                WalletEvents.PayoutStatusChanged p = event.payload(WalletEvents.PayoutStatusChanged.class);
                sendTo(RefType.PAYOUT, p.payoutId(), "PAYOUT_STATUS_CHANGED",
                        Map.of("amount", money(p.amountMinor(), p.currency()), "status", p.status()));
            }
            // ─── mlm ─────────────────────────────────────────────────────────
            case EventTypes.MLM_ACCESS_ACTIVATED -> {
                MlmEvents.MlmAccessActivated p = event.payload(MlmEvents.MlmAccessActivated.class);
                sendTo(RefType.MLM_USER, p.mlmUserId(), "MLM_ACCESS_ACTIVATED", Map.of(
                        "deadline", p.deadline(), "required", money(p.requiredAmountMinor(), p.currency())));
            }
            case EventTypes.MLM_ACTIVATED -> {
                MlmEvents.MlmActivated p = event.payload(MlmEvents.MlmActivated.class);
                sendTo(RefType.MLM_USER, p.mlmUserId(), "MLM_ACTIVATED", Map.of());
            }
            case EventTypes.MLM_EXPIRED -> {
                MlmEvents.MlmExpired p = event.payload(MlmEvents.MlmExpired.class);
                sendTo(RefType.MLM_USER, p.mlmUserId(), "MLM_EXPIRED", Map.of("deadline", p.deadline()));
            }
            // ─── команды ─────────────────────────────────────────────────────
            case EventTypes.SEND_NOTIFICATION -> {
                NotificationCommands.SendNotification p = event.payload(NotificationCommands.SendNotification.class);
                Channel channel = p.channel() == null ? null : Channel.valueOf(p.channel().toUpperCase());
                notifications.notify(p.userId(), p.templateCode(), p.payload(), channel);
            }
            default -> log.trace("ignoring {}", event.eventType());
        }
    }

    private Optional<UUID> walletOwner(String ownerType, String ownerRef) {
        return switch (ownerType) {
            case "SHOP" -> directory.find(RefType.SHOP, ownerRef);
            case "COURIER" -> directory.find(RefType.COURIER, ownerRef);
            default -> Optional.empty(); // кошельки платформы — без уведомлений
        };
    }

    private void sendTo(RefType type, Object refId, String template, Map<String, ?> payload) {
        directory.find(type, refId).ifPresentOrElse(
                userId -> send(userId, template, payload),
                () -> log.debug("no recipient known for {}:{} ({})", type, refId, template));
    }

    private void send(UUID userId, String template, Map<String, ?> payload) {
        notifications.notify(userId, template, payload, null);
    }

    private static String money(long minor, String currency) {
        return String.format("%d.%02d %s", minor / 100, Math.abs(minor % 100), currency);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
