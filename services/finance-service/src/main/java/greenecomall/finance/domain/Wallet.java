package greenecomall.finance.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.finance.FinanceErrors;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Внутренний кошелёк (виртуальный баланс, не реальный счёт). Источник истины — ledger
 * {@link WalletTransaction}; {@code balanceMinor} — денормализованный итог.
 * {@code heldMinor} — сумма, зарезервированная под заявки на вывод.
 */
@Entity
@Table(name = "wallets", uniqueConstraints =
        @UniqueConstraint(name = "uq_wallets_owner", columnNames = {"owner_type", "owner_ref", "currency"}))
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16, updatable = false)
    private WalletOwnerType ownerType;

    @Column(name = "owner_ref", nullable = false, length = 128, updatable = false)
    private String ownerRef;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "held_minor", nullable = false)
    private long heldMinor;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public Wallet(WalletOwnerType ownerType, String ownerRef, String currency) {
        this.ownerType = ownerType;
        this.ownerRef = ownerRef;
        this.currency = currency;
        this.balanceMinor = 0L;
        this.heldMinor = 0L;
    }

    public long applyCredit(long amountMinor) {
        this.balanceMinor = Math.addExact(this.balanceMinor, amountMinor);
        return this.balanceMinor;
    }

    public long applyDebit(long amountMinor) {
        if (amountMinor > available()) {
            throw new DomainException(FinanceErrors.WALLET_INSUFFICIENT_FUNDS,
                    "wallet " + id + " has " + available() + " available, cannot debit " + amountMinor);
        }
        this.balanceMinor = Math.subtractExact(this.balanceMinor, amountMinor);
        return this.balanceMinor;
    }

    /** Списание без проверки остатка — баланс может стать отрицательным (долг владельца). */
    public long applyDebitAllowingDebt(long amountMinor) {
        this.balanceMinor = Math.subtractExact(this.balanceMinor, amountMinor);
        return this.balanceMinor;
    }

    public void hold(long amountMinor) {
        if (amountMinor > available()) {
            throw new DomainException(FinanceErrors.WALLET_INSUFFICIENT_FUNDS,
                    "wallet " + id + " has " + available() + " available, cannot hold " + amountMinor);
        }
        this.heldMinor = Math.addExact(this.heldMinor, amountMinor);
    }

    public void releaseHold(long amountMinor) {
        this.heldMinor = Math.max(this.heldMinor - amountMinor, 0L);
    }

    public long available() {
        return balanceMinor - heldMinor;
    }

    public UUID getId() {
        return id;
    }

    public WalletOwnerType getOwnerType() {
        return ownerType;
    }

    public String getOwnerRef() {
        return ownerRef;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public long getHeldMinor() {
        return heldMinor;
    }
}
