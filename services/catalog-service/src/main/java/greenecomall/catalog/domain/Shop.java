package greenecomall.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    /** Адрес, по которому курьер забирает заказ. */
    @Column(length = 300)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legal_info")
    private String legalInfo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false, updatable = false)
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false, updatable = false)
    private City city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShopStatus status = ShopStatus.MODERATION;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(nullable = false, precision = 2, scale = 1)
    private java.math.BigDecimal rating = java.math.BigDecimal.ZERO;

    @Column(name = "reviews_count", nullable = false)
    private int reviewsCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shop() {
    }

    public Shop(UUID ownerUserId, String name, String legalInfo, Country country, City city) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.legalInfo = legalInfo;
        this.country = country;
        this.city = city;
        this.status = ShopStatus.MODERATION;
    }

    public void approve() {
        this.status = ShopStatus.ACTIVE;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        this.status = ShopStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void suspend(String reason) {
        this.status = ShopStatus.SUSPENDED;
        this.rejectionReason = reason;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerUserId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getLegalInfo() {
        return legalInfo;
    }

    public Country getCountry() {
        return country;
    }

    public City getCity() {
        return city;
    }

    public ShopStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public java.math.BigDecimal getRating() {
        return rating;
    }

    public int getReviewsCount() {
        return reviewsCount;
    }

    /** Денормализованный рейтинг по видимым отзывам (пересчитывает {@code ReviewService}). */
    public void applyRating(BigDecimal rating, int reviewsCount) {
        this.rating = rating;
        this.reviewsCount = reviewsCount;
    }

    /** Профиль магазина: что владелец может менять сам (название и реквизиты — до модерации). */
    public void updateProfile(String name, String legalInfo, String address, String phone) {
        this.name = name;
        this.legalInfo = legalInfo;
        this.address = address;
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }
}
