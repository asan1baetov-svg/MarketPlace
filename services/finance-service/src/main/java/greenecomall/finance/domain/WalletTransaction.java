package greenecomall.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Проводка ledger (append-only, без UPDATE/DELETE). {@code idempotencyKey} = {@code {eventId}:{walletId}:{type}}
 * — повторный {@code OrderPaid} не задваивает начисления (docs/ARCHITECTURE.md §2.4).
 */
@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wtx_wallet", columnList = "wallet_id, created_at"),
        @Index(name = "idx_wtx_reference", columnList = "reference_type, reference_id")
})
public class WalletTransaction {

    public enum Direction {CREDIT, DEBIT}

    public enum Type {
        ORDER_SETTLEMENT, COMMISSION, PAYOUT, REFUND, ADJUSTMENT, MLM_BONUS, COURIER_FEE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    private Type type;

    @Column(name = "reference_type", length = 40, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", length = 128, updatable = false)
    private String referenceId;

    @Column(name = "balance_after_minor", nullable = false, updatable = false)
    private long balanceAfterMinor;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 200)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransaction() {
    }

    public WalletTransaction(UUID walletId, Direction direction, long amountMinor, String currency, Type type,
                             String referenceType, String referenceId, long balanceAfterMinor, String idempotencyKey) {
        this.walletId = walletId;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.balanceAfterMinor = balanceAfterMinor;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Type getType() {
        return type;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public long getBalanceAfterMinor() {
        return balanceAfterMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
