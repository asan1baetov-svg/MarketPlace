package greenecomall.finance.domain;

public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED,
    /** Заказ отменён до оплаты; поздний успех от эквайера возвращается автоматически. */
    CANCELLED
}
