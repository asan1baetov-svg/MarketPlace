package greenecomall.mlm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Заказ MLM-клиента: снимок из {@code orders.OrderCreated}, засчитывается при {@code payments.OrderPaid},
 * откатывается при отмене/возврате. Заказы внешних клиентов сюда не попадают.
 */
@Entity
@Table(name = "mlm_orders")
public class MlmOrder {

    public enum Status {
        CREATED,
        /** Оплачен и засчитан в окно активации. */
        COUNTED,
        /** Оплачен, но в окно не попал (аккаунт уже активен/окно не открыто) — всё равно передаётся во внешний MLM. */
        PAID,
        REVERSED
    }

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "mlm_account_id", nullable = false, updatable = false)
    private UUID mlmAccountId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.CREATED;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MlmOrder() {
    }

    public MlmOrder(UUID orderId, UUID mlmAccountId, long amountMinor, String currency) {
        this.orderId = orderId;
        this.mlmAccountId = mlmAccountId;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public void markPaid(boolean counted, Instant paidAt) {
        this.status = counted ? Status.COUNTED : Status.PAID;
        this.paidAt = paidAt;
    }

    public void markReversed() {
        this.status = Status.REVERSED;
    }

    public boolean isPaid() {
        return status == Status.COUNTED || status == Status.PAID;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getMlmAccountId() {
        return mlmAccountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
