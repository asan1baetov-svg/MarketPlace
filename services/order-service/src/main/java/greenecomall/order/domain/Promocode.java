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
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** Промокод. Кто финансирует скидку — в {@link PromocodeFundedBy} (влияет на расчёт в finance). */
@Entity
@Table(name = "promocodes")
public class Promocode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromocodeType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(name = "funded_by", nullable = false, length = 16)
    private PromocodeFundedBy fundedBy;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Promocode() {
    }

    public Promocode(String code, PromocodeType type, BigDecimal value, PromocodeFundedBy fundedBy,
                     Instant validFrom, Instant validTo, Integer usageLimit) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.fundedBy = fundedBy;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.usageLimit = usageLimit;
        this.active = true;
    }

    public void assertUsable(Instant now) {
        if (!active) {
            throw new DomainException(OrderErrors.PROMOCODE_INVALID, "promocode " + code + " is inactive");
        }
        if (now.isBefore(validFrom) || now.isAfter(validTo)) {
            throw new DomainException(OrderErrors.PROMOCODE_INVALID, "promocode " + code + " is outside its validity window");
        }
        if (usageLimit != null && usedCount >= usageLimit) {
            throw new DomainException(OrderErrors.PROMOCODE_INVALID, "promocode " + code + " usage limit reached");
        }
    }

    /**
     * Скидка в минорных единицах на базовую сумму заказа; не больше самой суммы. Округляется вниз
     * до целой единицы валюты: эквайринг (Finik) принимает сумму только в целых сомах.
     */
    public long computeDiscountMinor(long baseMinor) {
        long raw = switch (type) {
            case PERCENT -> BigDecimal.valueOf(baseMinor)
                    .multiply(value)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            case FIXED -> value.setScale(0, RoundingMode.HALF_UP).longValueExact();
        };
        long wholeUnits = Math.max(raw, 0) / 100 * 100;
        return Math.min(wholeUnits, baseMinor);
    }

    public void recordUse() {
        this.usedCount++;
    }

    public void update(BigDecimal value, Instant validFrom, Instant validTo, Integer usageLimit, boolean active) {
        this.value = value;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.usageLimit = usageLimit;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public PromocodeType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public PromocodeFundedBy getFundedBy() {
        return fundedBy;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public boolean isActive() {
        return active;
    }
}
