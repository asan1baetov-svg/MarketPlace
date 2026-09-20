package greenecomall.finance.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.finance.FinanceErrors;
import greenecomall.finance.finik.FinikBank;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Куда переводить деньги магазину: банк + номер телефона. Автовыплаты идут только на реквизиты,
 * одобренные админом (защита от подмены реквизитов через взломанный аккаунт магазина).
 */
@Entity
@Table(name = "payout_requisites")
public class PayoutRequisite {

    public enum Status {PENDING, APPROVED, BLOCKED}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16, updatable = false)
    private WalletOwnerType ownerType;

    @Column(name = "owner_ref", nullable = false, length = 128, updatable = false)
    private String ownerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private FinikBank bank;

    @Column(nullable = false, length = 20, updatable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected PayoutRequisite() {
    }

    public PayoutRequisite(WalletOwnerType ownerType, String ownerRef, FinikBank bank, String phone, Instant now) {
        this.ownerType = ownerType;
        this.ownerRef = ownerRef;
        this.bank = bank;
        this.phone = phone;
        this.status = Status.PENDING;
        this.createdAt = now;
    }

    public void approve(Instant now) {
        if (status != Status.PENDING) {
            throw new DomainException(FinanceErrors.REQUISITE_STATUS_INVALID, "requisite " + id + " is " + status);
        }
        this.status = Status.APPROVED;
        this.decidedAt = now;
    }

    public void block(Instant now) {
        this.status = Status.BLOCKED;
        this.decidedAt = now;
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

    public FinikBank getBank() {
        return bank;
    }

    public String getPhone() {
        return phone;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
