package greenecomall.courier.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.courier.CourierErrors;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Доставка одного suborder (MVP: один курьер на suborder, docs/ARCHITECTURE.md §8.2 вопрос 1).
 * Создаётся из {@code orders.OrderCreated}, становится доступной для подбора после {@code payments.OrderPaid}.
 */
@Entity
@Table(name = "delivery_jobs")
public class DeliveryJob {

    public enum Status {
        /** Заказ создан, но ещё не оплачен — курьера не ищем. */
        CREATED,
        /** Оплачен, ждёт курьера. */
        AWAITING_COURIER,
        /** Оффер отправлен курьеру {@code courierId}, ждём ответа до {@code offerExpiresAt}. */
        OFFERED,
        ACCEPTED,
        PICKED_UP,
        IN_TRANSIT,
        DELIVERED,
        FAILED,
        /** В городе нет свободных курьеров — периодически пробуем снова, админ уведомлён. */
        NO_COURIER,
        CANCELLED
    }

    @Id
    @Column(name = "suborder_id", nullable = false, updatable = false)
    private UUID suborderId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "shop_id", nullable = false, updatable = false)
    private UUID shopId;

    @Column(name = "city_id", nullable = false, updatable = false)
    private UUID cityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CREATED;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    @Column(name = "ready_for_pickup", nullable = false)
    private boolean readyForPickup;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryJob() {
    }

    public DeliveryJob(UUID suborderId, UUID orderId, UUID shopId, UUID cityId) {
        this.suborderId = suborderId;
        this.orderId = orderId;
        this.shopId = shopId;
        this.cityId = cityId;
        this.status = Status.CREATED;
    }

    public void markPaid() {
        if (status == Status.CREATED) {
            status = Status.AWAITING_COURIER;
        }
    }

    public void offerTo(UUID courierId, Instant expiresAt) {
        require(Status.AWAITING_COURIER, Status.NO_COURIER);
        this.courierId = courierId;
        this.offerExpiresAt = expiresAt;
        this.status = Status.OFFERED;
    }

    /** Оффер отклонён/истёк — задача возвращается в очередь подбора. */
    public void returnToQueue() {
        require(Status.OFFERED);
        this.courierId = null;
        this.offerExpiresAt = null;
        this.status = Status.AWAITING_COURIER;
    }

    public void markNoCourier() {
        require(Status.AWAITING_COURIER, Status.NO_COURIER);
        this.status = Status.NO_COURIER;
    }

    public void accept(UUID courierId, Instant now) {
        require(Status.OFFERED);
        requireCourier(courierId);
        this.status = Status.ACCEPTED;
        this.offerExpiresAt = null;
        this.acceptedAt = now;
    }

    public void pickUp(UUID courierId) {
        require(Status.ACCEPTED);
        requireCourier(courierId);
        if (!readyForPickup) {
            throw new DomainException(CourierErrors.JOB_NOT_READY_FOR_PICKUP,
                    "suborder " + suborderId + " is not assembled by the shop yet");
        }
        this.status = Status.PICKED_UP;
    }

    public void startTransit(UUID courierId) {
        require(Status.PICKED_UP);
        requireCourier(courierId);
        this.status = Status.IN_TRANSIT;
    }

    public void deliver(UUID courierId, Instant now) {
        require(Status.IN_TRANSIT);
        requireCourier(courierId);
        this.status = Status.DELIVERED;
        this.deliveredAt = now;
    }

    public void fail(UUID courierId, String reason) {
        require(Status.ACCEPTED, Status.PICKED_UP, Status.IN_TRANSIT);
        requireCourier(courierId);
        this.status = Status.FAILED;
        this.failureReason = reason;
    }

    public void markReadyForPickup() {
        this.readyForPickup = true;
    }

    public void cancel() {
        if (status != Status.DELIVERED && status != Status.FAILED) {
            this.status = Status.CANCELLED;
        }
    }

    /** Ручное назначение админом — перетирает текущий оффер. */
    public void assignManually(UUID courierId, Instant now) {
        require(Status.AWAITING_COURIER, Status.NO_COURIER, Status.OFFERED);
        this.courierId = courierId;
        this.offerExpiresAt = null;
        this.status = Status.ACCEPTED;
        this.acceptedAt = now;
    }

    private void require(Status... allowed) {
        for (Status s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new DomainException(CourierErrors.JOB_STATUS_INVALID,
                "delivery of suborder " + suborderId + " is " + status);
    }

    private void requireCourier(UUID courierId) {
        if (this.courierId == null || !this.courierId.equals(courierId)) {
            throw new DomainException(CourierErrors.JOB_FORBIDDEN,
                    "delivery of suborder " + suborderId + " is not assigned to courier " + courierId);
        }
    }

    public UUID getSuborderId() {
        return suborderId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getShopId() {
        return shopId;
    }

    public UUID getCityId() {
        return cityId;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public Instant getOfferExpiresAt() {
        return offerExpiresAt;
    }

    public boolean isReadyForPickup() {
        return readyForPickup;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
