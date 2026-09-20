package greenecomall.courier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Начисление курьеру за доставку — локальный отчёт «заработок за период». Денежный учёт
 * (кошелёк курьера) ведёт finance-service по {@code couriers.DeliveryCompleted}.
 */
@Entity
@Table(name = "courier_earnings")
public class CourierEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Column(name = "suborder_id", nullable = false, unique = true, updatable = false)
    private UUID suborderId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CourierEarning() {
    }

    public CourierEarning(UUID courierId, UUID suborderId, long amountMinor, String currency) {
        this.courierId = courierId;
        this.suborderId = suborderId;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public UUID getSuborderId() {
        return suborderId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
