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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Часть заказа по одному магазину: независимый учёт статуса, сборки и выплаты.
 * {@code goodsAmountMinor} — Σ цен с наценкой; {@code costAmountMinor} — Σ себестоимости (выплата
 * магазину); {@code platformCommissionMinor} — разница (± корректировка на промокод в finance).
 */
@Entity
@Table(name = "suborders", indexes = {
        @Index(name = "idx_suborders_order", columnList = "order_id"),
        @Index(name = "idx_suborders_shop_status", columnList = "shop_id, status")
})
public class Suborder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "shop_id", nullable = false, updatable = false)
    private UUID shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SuborderStatus status = SuborderStatus.CREATED;

    @Column(name = "goods_amount_minor", nullable = false)
    private long goodsAmountMinor;

    @Column(name = "cost_amount_minor", nullable = false)
    private long costAmountMinor;

    @Column(name = "platform_commission_minor", nullable = false)
    private long platformCommissionMinor;

    @Column(name = "courier_id")
    private UUID courierId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Suborder() {
    }

    public Suborder(UUID orderId, UUID shopId, long goodsAmountMinor, long costAmountMinor) {
        this.orderId = orderId;
        this.shopId = shopId;
        this.goodsAmountMinor = goodsAmountMinor;
        this.costAmountMinor = costAmountMinor;
        this.platformCommissionMinor = goodsAmountMinor - costAmountMinor;
        this.status = SuborderStatus.CREATED;
    }

    /** Переход по конечному автомату; бросает {@link DomainException}, если переход запрещён. */
    public SuborderStatus transitionTo(SuborderStatus target) {
        if (status == target) {
            return status;
        }
        if (!status.canTransitionTo(target)) {
            throw new DomainException(OrderErrors.SUBORDER_STATUS_INVALID,
                    "suborder " + id + ": " + status + " -> " + target + " is not allowed");
        }
        SuborderStatus from = this.status;
        this.status = target;
        return from;
    }

    public void assignCourier(UUID courierId) {
        this.courierId = courierId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getShopId() {
        return shopId;
    }

    public SuborderStatus getStatus() {
        return status;
    }

    public long getGoodsAmountMinor() {
        return goodsAmountMinor;
    }

    public long getCostAmountMinor() {
        return costAmountMinor;
    }

    public long getPlatformCommissionMinor() {
        return platformCommissionMinor;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
