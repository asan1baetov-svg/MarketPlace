package greenecomall.courier.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.courier.CourierErrors;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Курьер: привязка к городу/зонам, модерация, рабочий статус (docs/ARCHITECTURE.md §3.5). */
@Entity
@Table(name = "couriers")
public class Courier {

    public enum Status {ACTIVE, BUSY, OFFLINE}

    public enum Moderation {PENDING, APPROVED, REJECTED}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "country_id", nullable = false)
    private UUID countryId;

    @Column(name = "city_id", nullable = false)
    private UUID cityId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "courier_delivery_zones", joinColumns = @JoinColumn(name = "courier_id"))
    @Column(name = "delivery_zone_id", nullable = false)
    private Set<UUID> zoneIds = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 16)
    private Moderation moderationStatus = Moderation.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String vehicle;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Courier() {
    }

    public Courier(UUID userId, UUID countryId, UUID cityId, Set<UUID> zoneIds, String vehicle) {
        this.userId = userId;
        this.countryId = countryId;
        this.cityId = cityId;
        this.zoneIds = zoneIds == null ? new HashSet<>() : new HashSet<>(zoneIds);
        this.vehicle = vehicle;
        this.status = Status.OFFLINE;
        this.moderationStatus = Moderation.PENDING;
    }

    public void approve() {
        requirePending();
        this.moderationStatus = Moderation.APPROVED;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        requirePending();
        this.moderationStatus = Moderation.REJECTED;
        this.rejectionReason = reason;
    }

    /** Курьер сам выходит на линию / уходит с линии. BUSY выставляет только диспетчеризация. */
    public void changeStatus(Status target) {
        if (moderationStatus != Moderation.APPROVED) {
            throw new DomainException(CourierErrors.COURIER_NOT_APPROVED, "courier " + id + " is not approved");
        }
        this.status = target;
    }

    public boolean isDispatchable() {
        return moderationStatus == Moderation.APPROVED && status == Status.ACTIVE;
    }

    private void requirePending() {
        if (moderationStatus != Moderation.PENDING) {
            throw new DomainException(CourierErrors.MODERATION_STATUS_INVALID,
                    "courier " + id + " is already " + moderationStatus);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCountryId() {
        return countryId;
    }

    public UUID getCityId() {
        return cityId;
    }

    public Set<UUID> getZoneIds() {
        return zoneIds;
    }

    public Status getStatus() {
        return status;
    }

    public Moderation getModerationStatus() {
        return moderationStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public String getVehicle() {
        return vehicle;
    }
}
