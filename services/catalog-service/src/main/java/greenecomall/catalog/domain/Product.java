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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false, updatable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 300)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductUnit unit = ProductUnit.PCS;

    @Column(name = "cost_price_minor", nullable = false)
    private long costPriceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false, updatable = false)
    private City city;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false, updatable = false)
    private Country country;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "reviews_count", nullable = false)
    private int reviewsCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(Shop shop, Category category, String name, String description, ProductUnit unit,
                   long costPriceMinor, String currency) {
        this.shop = shop;
        this.category = category;
        this.name = name;
        this.description = description;
        this.unit = unit != null ? unit : ProductUnit.PCS;
        this.costPriceMinor = costPriceMinor;
        this.currency = currency;
        this.city = shop.getCity();
        this.country = shop.getCountry();
        this.status = ProductStatus.DRAFT;
    }

    public void update(Category category, String name, String description, ProductUnit unit,
                       long costPriceMinor, String currency) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.unit = unit != null ? unit : this.unit;
        this.costPriceMinor = costPriceMinor;
        this.currency = currency;
    }

    public void submitForModeration() {
        this.status = ProductStatus.MODERATION;
    }

    public void publish() {
        this.status = ProductStatus.PUBLISHED;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        this.status = ProductStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void archive() {
        this.status = ProductStatus.ARCHIVED;
    }

    public UUID getId() {
        return id;
    }

    public Shop getShop() {
        return shop;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public long getCostPriceMinor() {
        return costPriceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public City getCity() {
        return city;
    }

    public Country getCountry() {
        return country;
    }

    public BigDecimal getRating() {
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
}
