package greenecomall.order.domain;

/**
 * Статус заказа клиента — агрегат из статусов его suborders (см. docs/ARCHITECTURE.md §3.3).
 */
public enum OrderStatus {
    CREATED,
    PAID,
    PARTIALLY_DELIVERED,
    COMPLETED,
    CANCELLED,
    PAYMENT_FAILED
}
