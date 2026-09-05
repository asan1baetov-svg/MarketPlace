package greenecomall.common.events;

/**
 * Имена Kafka-топиков. Топик = агрегат-домен, конкретный тип события — в {@link EventEnvelope#eventType()}.
 * Ключ партиции = id корневого агрегата (orderId, shopId, mlmUserId), чтобы события одной сущности шли по порядку.
 */
public final class Topics {

    public static final String AUTH = "auth";
    public static final String CATALOG = "catalog";
    public static final String ORDERS = "orders";
    public static final String PAYMENTS = "payments";
    public static final String WALLET = "wallet";
    public static final String COURIERS = "couriers";
    public static final String MLM = "mlm";
    public static final String NOTIFICATION_COMMANDS = "notifications.commands";

    private Topics() {
    }
}
