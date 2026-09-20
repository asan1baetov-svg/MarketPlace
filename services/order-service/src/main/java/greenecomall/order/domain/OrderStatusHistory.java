package greenecomall.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Журнал переходов статусов заказа/suborder — для аудита и таймлайна в UI. */
@Entity
@Table(name = "order_status_history", indexes = {
        @Index(name = "idx_osh_order", columnList = "order_id"),
        @Index(name = "idx_osh_suborder", columnList = "suborder_id")
})
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "suborder_id")
    private UUID suborderId;

    @Column(name = "from_status", length = 24)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 24)
    private String toStatus;

    @Column(nullable = false, length = 64)
    private String actor;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderStatusHistory() {
    }

    private OrderStatusHistory(UUID orderId, UUID suborderId, String fromStatus, String toStatus,
                               String actor, String reason) {
        this.orderId = orderId;
        this.suborderId = suborderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.reason = reason;
    }

    public static OrderStatusHistory forOrder(UUID orderId, Object from, Object to, String actor, String reason) {
        return new OrderStatusHistory(orderId, null, from == null ? null : from.toString(), to.toString(), actor, reason);
    }

    public static OrderStatusHistory forSuborder(UUID suborderId, Object from, Object to, String actor, String reason) {
        return new OrderStatusHistory(null, suborderId, from == null ? null : from.toString(), to.toString(), actor, reason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getSuborderId() {
        return suborderId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
