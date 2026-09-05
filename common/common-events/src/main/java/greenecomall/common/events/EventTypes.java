package greenecomall.common.events;

/**
 * Стабильные строковые типы событий в формате {@code <topic>.<Event>}.
 * Полный каталог с payload/producer/consumer — в docs/ARCHITECTURE.md, раздел 4.
 */
public final class EventTypes {

    // auth
    public static final String USER_REGISTERED = "auth.UserRegistered";
    public static final String MLM_USER_LINKED = "auth.MlmUserLinked";

    // catalog
    public static final String SHOP_APPROVED = "catalog.ShopApproved";
    public static final String SHOP_SUSPENDED = "catalog.ShopSuspended";
    public static final String PRODUCT_PUBLISHED = "catalog.ProductPublished";
    public static final String PRODUCT_REJECTED = "catalog.ProductRejected";
    public static final String STOCK_CHANGED = "catalog.StockChanged";

    // orders
    public static final String ORDER_CREATED = "orders.OrderCreated";
    public static final String ORDER_CANCELLED = "orders.OrderCancelled";
    public static final String SUBORDER_STATUS_CHANGED = "orders.SuborderStatusChanged";
    public static final String ORDER_COMPLETED = "orders.OrderCompleted";

    // payments
    public static final String PAYMENT_INITIATED = "payments.PaymentInitiated";
    public static final String ORDER_PAID = "payments.OrderPaid";
    public static final String PAYMENT_FAILED = "payments.PaymentFailed";
    public static final String PAYMENT_REFUNDED = "payments.PaymentRefunded";
    public static final String MLM_ACCESS_PAID = "payments.MlmAccessPaid";

    // wallet
    public static final String WALLET_CREDITED = "wallet.WalletCredited";
    public static final String WALLET_DEBITED = "wallet.WalletDebited";
    public static final String PAYOUT_REQUESTED = "wallet.PayoutRequested";
    public static final String PAYOUT_APPROVED = "wallet.PayoutApproved";
    public static final String PAYOUT_PAID = "wallet.PayoutPaid";

    // couriers
    public static final String COURIER_REGISTERED = "couriers.CourierRegistered";
    public static final String COURIER_ASSIGNED = "couriers.CourierAssigned";
    public static final String COURIER_ACCEPTED = "couriers.CourierAccepted";
    public static final String DELIVERY_PICKED_UP = "couriers.DeliveryPickedUp";
    public static final String DELIVERY_IN_TRANSIT = "couriers.DeliveryInTransit";
    public static final String DELIVERY_COMPLETED = "couriers.DeliveryCompleted";
    public static final String DELIVERY_FAILED = "couriers.DeliveryFailed";
    public static final String NO_COURIER_AVAILABLE = "couriers.NoCourierAvailable";

    // mlm
    public static final String MLM_ACCESS_ACTIVATED = "mlm.MlmAccessActivated";
    public static final String MLM_PURCHASE_COUNTED = "mlm.MlmPurchaseCounted";
    public static final String MLM_CONDITION_MET = "mlm.MlmConditionMet";
    public static final String MLM_ACTIVATED = "mlm.MlmActivated";
    public static final String MLM_EXPIRED = "mlm.MlmExpired";
    public static final String MLM_REFERRAL_BONUS_ACCRUED = "mlm.MlmReferralBonusAccrued";

    // notifications
    public static final String SEND_NOTIFICATION = "notifications.SendNotification";

    private EventTypes() {
    }
}
