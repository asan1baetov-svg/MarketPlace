package greenecomall.courier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** История офферов по suborder — чтобы не предлагать одну и ту же доставку отказавшемуся курьеру. */
@Entity
@Table(name = "assignment_offers")
public class AssignmentOffer {

    public enum Status {PENDING, ACCEPTED, REJECTED, EXPIRED}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "suborder_id", nullable = false, updatable = false)
    private UUID suborderId;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @CreationTimestamp
    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected AssignmentOffer() {
    }

    public AssignmentOffer(UUID suborderId, UUID courierId, Instant expiresAt) {
        this.suborderId = suborderId;
        this.courierId = courierId;
        this.expiresAt = expiresAt;
        this.status = Status.PENDING;
    }

    public void close(Status status) {
        if (this.status == Status.PENDING) {
            this.status = status;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSuborderId() {
        return suborderId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public Status getStatus() {
        return status;
    }
}
