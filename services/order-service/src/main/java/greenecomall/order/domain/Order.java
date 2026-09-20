package greenecomall.order.domain;

import greenecomall.order.OrderErrors;
import greenecomall.common.domain.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Заказ клиента. На бэкенде дробится на {@link Suborder} (по одному на магазин); общий статус —
 * агрегат из статусов suborders (см. docs/ARCHITECTURE.md §3.3, §6).
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_client", columnList = "client_user_id, created_at"),
        @Index(name = "idx_orders_city_status", columnList = "city_id, status")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_user_id", nullable = false, updatable = false)
    private UUID clientUserId;

    @Column(name = "city_id", nullable = false, updatable = false)
    private UUID cityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "items_amount_minor", nullable = false)
    private long itemsAmountMinor;

    @Column(name = "discount_amount_minor", nullable = false)
    private long discountAmountMinor;

    @Column(name = "total_amount_minor", nullable = false)
    private long totalAmountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "promocode_id")
    private UUID promocodeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(UUID clientUserId, UUID cityId, String deliveryAddress, String currency,
                 long itemsAmountMinor, long discountAmountMinor, UUID promocodeId) {
        this.clientUserId = clientUserId;
        this.cityId = cityId;
        this.deliveryAddress = deliveryAddress;
        this.currency = currency;
        this.itemsAmountMinor = itemsAmountMinor;
        this.discountAmountMinor = discountAmountMinor;
        this.totalAmountMinor = Math.max(itemsAmountMinor - discountAmountMinor, 0);
        this.promocodeId = promocodeId;
        this.status = OrderStatus.CREATED;
    }

    public void markPaid(UUID paymentId) {
        if (status != OrderStatus.CREATED) {
            throw new DomainException(OrderErrors.ORDER_STATUS_INVALID,
                    "order " + id + " is " + status + ", cannot mark paid");
        }
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
    }

    public void markPaymentFailed() {
        if (status != OrderStatus.CREATED) {
            throw new DomainException(OrderErrors.ORDER_STATUS_INVALID,
                    "order " + id + " is " + status + ", cannot mark payment failed");
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
            throw new DomainException(OrderErrors.ORDER_STATUS_INVALID,
                    "order " + id + " is " + status + ", cannot cancel");
        }
        this.status = OrderStatus.CANCELLED;
    }

    /** Пересчитывает агрегатный статус заказа из статусов его suborders. */
    public void recomputeStatus(List<Suborder> suborders) {
        if (suborders.isEmpty()) {
            return;
        }
        boolean allCompleted = suborders.stream().allMatch(s -> s.getStatus() == SuborderStatus.COMPLETED);
        boolean allCancelled = suborders.stream().allMatch(s -> s.getStatus() == SuborderStatus.CANCELLED);
        boolean anyDelivered = suborders.stream()
                .anyMatch(s -> s.getStatus() == SuborderStatus.DELIVERED || s.getStatus() == SuborderStatus.COMPLETED);
        boolean anyOpen = suborders.stream().anyMatch(s -> !s.getStatus().isTerminal());
        if (allCompleted) {
            this.status = OrderStatus.COMPLETED;
        } else if (allCancelled) {
            this.status = OrderStatus.CANCELLED;
        } else if (anyDelivered && anyOpen) {
            this.status = OrderStatus.PARTIALLY_DELIVERED;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientUserId() {
        return clientUserId;
    }

    public UUID getCityId() {
        return cityId;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getItemsAmountMinor() {
        return itemsAmountMinor;
    }

    public long getDiscountAmountMinor() {
        return discountAmountMinor;
    }

    public long getTotalAmountMinor() {
        return totalAmountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getPromocodeId() {
        return promocodeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
