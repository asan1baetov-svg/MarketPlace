package greenecomall.mlm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Начисление реферального бонуса (только при {@code mlm.local-bonuses-enabled=true}). */
@Entity
@Table(name = "mlm_bonus_transactions", uniqueConstraints =
        @UniqueConstraint(name = "uq_bonus_source_account", columnNames = {"source_ref", "mlm_account_id"}))
public class MlmBonusTransaction {

    public enum Status {ACCRUED, REVERSED}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "mlm_account_id", nullable = false, updatable = false)
    private UUID mlmAccountId;

    @Column(name = "source_type", nullable = false, length = 24, updatable = false)
    private String sourceType;

    @Column(name = "source_ref", nullable = false, length = 128, updatable = false)
    private String sourceRef;

    @Column(name = "from_account_id", nullable = false, updatable = false)
    private UUID fromAccountId;

    @Column(nullable = false, updatable = false)
    private int level;

    @Column(nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal percent;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.ACCRUED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MlmBonusTransaction() {
    }

    public MlmBonusTransaction(UUID mlmAccountId, String sourceType, String sourceRef, UUID fromAccountId,
                               int level, BigDecimal percent, long amountMinor, String currency) {
        this.mlmAccountId = mlmAccountId;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.fromAccountId = fromAccountId;
        this.level = level;
        this.percent = percent;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public void reverse() {
        this.status = Status.REVERSED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMlmAccountId() {
        return mlmAccountId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public int getLevel() {
        return level;
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
