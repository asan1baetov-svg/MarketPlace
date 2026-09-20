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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "markup_rules")
public class MarkupRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarkupScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(name = "markup_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal markupPercent;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MarkupRule() {
    }

    public MarkupRule(MarkupScope scope, Category category, Shop shop, Product product, Country country,
                      City city, BigDecimal markupPercent, int priority, boolean active) {
        this.scope = scope;
        this.category = category;
        this.shop = shop;
        this.product = product;
        this.country = country;
        this.city = city;
        this.markupPercent = markupPercent;
        this.priority = priority;
        this.active = active;
    }

    public void update(BigDecimal markupPercent, int priority, boolean active) {
        this.markupPercent = markupPercent;
        this.priority = priority;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public MarkupScope getScope() {
        return scope;
    }

    public Category getCategory() {
        return category;
    }

    public Shop getShop() {
        return shop;
    }

    public Product getProduct() {
        return product;
    }

    public Country getCountry() {
        return country;
    }

    public City getCity() {
        return city;
    }

    public BigDecimal getMarkupPercent() {
        return markupPercent;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }
}
