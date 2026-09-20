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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refunds_payment", columnList = "payment_id"))
public class Refund {

    public enum Status {REQUESTED, SUCCEEDED, FAILED}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.REQUESTED;

    @Column(name = "provider_refund_id", length = 200)
    private String providerRefundId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Refund() {
    }

    public Refund(UUID paymentId, long amountMinor, String reason) {
        this.paymentId = paymentId;
        this.amountMinor = amountMinor;
        this.reason = reason;
        this.status = Status.REQUESTED;
    }

    public void markSucceeded(String providerRefundId) {
        this.status = Status.SUCCEEDED;
        this.providerRefundId = providerRefundId;
    }

    /** Возврат сделан вручную в кабинете провайдера (у Finik нет API возврата). */
    public void completeManually(String reference) {
        if (status != Status.REQUESTED) {
            throw new DomainException(FinanceErrors.PAYMENT_STATUS_INVALID, "refund " + id + " is " + status);
        }
        markSucceeded(reference);
    }

    public String getReason() {
        return reason;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Status getStatus() {
        return status;
    }
}
