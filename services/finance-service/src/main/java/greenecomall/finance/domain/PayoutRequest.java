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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Заявка магазина/курьера на вывод средств. В MVP подтверждается админом вручную
 * (docs/ARCHITECTURE.md §8.2 вопрос 8); стейт-машина готова и под авто-выплаты через API эквайера.
 */
@Entity
@Table(name = "payout_requests", indexes = @Index(name = "idx_payouts_wallet", columnList = "wallet_id"))
public class PayoutRequest {

    /**
     * Ручная заявка: REQUESTED → APPROVED → PAID | REJECTED. Автовыплата: QUEUED → SENDING → PAID,
     * при окончательном отказе — FAILED (сумма остаётся в hold; админ повторяет, платит вручную или отменяет).
     */
    public enum Status {REQUESTED, APPROVED, REJECTED, PAID, QUEUED, SENDING, FAILED}

    /** Сколько раз повторять перевод при временных сбоях (сеть, 5xx) до статуса FAILED. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.REQUESTED;

    @Column(name = "requested_by", nullable = false, length = 128, updatable = false)
    private String requestedBy;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bank_details", nullable = false, updatable = false)
    private String bankDetails;

    @Column(name = "provider_payout_id", length = 200)
    private String providerPayoutId;

    @Column(nullable = false, updatable = false)
    private boolean auto;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "requisite_id")
    private UUID requisiteId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected PayoutRequest() {
    }

    public PayoutRequest(UUID walletId, long amountMinor, String currency, String requestedBy, String bankDetails) {
        this.walletId = walletId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.requestedBy = requestedBy;
        this.bankDetails = bankDetails;
        this.status = Status.REQUESTED;
    }

    /** Автовыплата после оплаты заказа; {@code reference} (например, {@code order:<id>}) — чтобы отменить до отправки. */
    public static PayoutRequest auto(UUID walletId, long amountMinor, String currency, String reference, Instant now) {
        PayoutRequest p = new PayoutRequest(walletId, amountMinor, currency, "auto:" + reference, "{}");
        p.auto = true;
        p.status = Status.QUEUED;
        p.nextAttemptAt = now;
        return p;
    }

    public void startSending(UUID requisiteId) {
        requireStatus(Status.QUEUED);
        this.status = Status.SENDING;
        this.requisiteId = requisiteId;
        this.attempts++;
    }

    /** Нет одобренных реквизитов — ждём, попытка не расходуется. */
    public void waitForRequisite(Instant retryAt) {
        requireStatus(Status.QUEUED);
        this.lastError = "no approved payout requisite";
        this.nextAttemptAt = retryAt;
    }

    public void markSent(String providerPayoutId, Instant now) {
        requireStatus(Status.SENDING);
        this.status = Status.PAID;
        this.providerPayoutId = providerPayoutId;
        this.lastError = null;
        this.processedAt = now;
    }

    /** Временный сбой: вернуть в очередь, после {@link #MAX_ATTEMPTS} — FAILED. */
    public void retryLater(String error, Instant retryAt, Instant now) {
        requireStatus(Status.SENDING);
        this.lastError = truncate(error);
        if (attempts >= MAX_ATTEMPTS) {
            this.status = Status.FAILED;
            this.processedAt = now;
        } else {
            this.status = Status.QUEUED;
            this.nextAttemptAt = retryAt;
        }
    }

    public void fail(String error, Instant now) {
        requireStatus(Status.SENDING);
        this.status = Status.FAILED;
        this.lastError = truncate(error);
        this.processedAt = now;
    }

    /** Админ: повторить упавшую автовыплату (например, после исправления реквизитов). */
    public void requeue(Instant now) {
        requireStatus(Status.FAILED);
        this.status = Status.QUEUED;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public void approve(String approvedBy, Instant now) {
        requireRequestedOrFailed();
        this.status = Status.APPROVED;
        this.approvedBy = approvedBy;
        this.processedAt = now;
    }

    public void reject(String approvedBy, Instant now) {
        if (status != Status.QUEUED) {
            requireRequestedOrFailed();
        }
        this.status = Status.REJECTED;
        this.approvedBy = approvedBy;
        this.processedAt = now;
    }

    public void markPaid(String providerPayoutId, Instant now) {
        if (status != Status.APPROVED) {
            throw new DomainException(FinanceErrors.PAYOUT_STATUS_INVALID,
                    "payout " + id + " is " + status + ", cannot mark paid");
        }
        this.status = Status.PAID;
        this.providerPayoutId = providerPayoutId;
        this.processedAt = now;
    }

    /** FAILED-автовыплату админ может провести вручную (approve) или отменить (reject). */
    private void requireRequestedOrFailed() {
        if (status != Status.REQUESTED && status != Status.FAILED) {
            throw new DomainException(FinanceErrors.PAYOUT_STATUS_INVALID,
                    "payout " + id + " is " + status + ", expected REQUESTED or FAILED");
        }
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new DomainException(FinanceErrors.PAYOUT_STATUS_INVALID,
                    "payout " + id + " is " + status + ", expected " + expected);
        }
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }

    public boolean isAuto() {
        return auto;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public UUID getRequisiteId() {
        return requisiteId;
    }

    public String getProviderPayoutId() {
        return providerPayoutId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
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

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
