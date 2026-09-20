package greenecomall.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * План распределения средств по заказу, снятый из {@code orders.OrderCreated}. Исполняется
 * при {@code payments.OrderPaid} — так wallet-модуль не делает синхронный вызов в order-service.
 */
@Entity
@Table(name = "order_settlement_plans")
public class OrderSettlementPlan {

    public enum Status {PENDING, SETTLED, REVERSED}

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String lines;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderSettlementPlan() {
    }

    public OrderSettlementPlan(UUID orderId, String currency, String linesJson) {
        this.orderId = orderId;
        this.currency = currency;
        this.lines = linesJson;
        this.status = Status.PENDING;
    }

    public void markSettled() {
        this.status = Status.SETTLED;
    }

    public void markReversed() {
        this.status = Status.REVERSED;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCurrency() {
        return currency;
    }

    public String getLinesJson() {
        return lines;
    }

    public Status getStatus() {
        return status;
    }

    /** Строка плана: сколько причитается магазину (cost) и платформе (commission) по одному suborder. */
    public record Line(UUID suborderId, UUID shopId, long goodsAmountMinor, long costAmountMinor) {

        public long commissionMinor() {
            return goodsAmountMinor - costAmountMinor;
        }
    }

    public record Lines(List<Line> items) {
    }
}
