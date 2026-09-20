package greenecomall.mlm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

/** Процент реферального бонуса на уровне {@code level} (1 — прямой пригласивший). */
@Entity
@Table(name = "mlm_referral_rates", uniqueConstraints =
        @UniqueConstraint(name = "uq_referral_rates_tariff_level", columnNames = {"tariff_id", "level"}))
public class MlmReferralRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tariff_id", nullable = false, updatable = false)
    private UUID tariffId;

    @Column(nullable = false, updatable = false)
    private int level;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    protected MlmReferralRate() {
    }

    public MlmReferralRate(UUID tariffId, int level, BigDecimal percent) {
        this.tariffId = tariffId;
        this.level = level;
        this.percent = percent;
    }

    public void setPercent(BigDecimal percent) {
        this.percent = percent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTariffId() {
        return tariffId;
    }

    public int getLevel() {
        return level;
    }

    public BigDecimal getPercent() {
        return percent;
    }
}
