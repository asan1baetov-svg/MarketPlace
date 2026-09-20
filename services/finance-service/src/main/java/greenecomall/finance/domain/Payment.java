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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Онлайн-платёж через эквайринг. Деньги приходят на единый счёт платформы (см. docs/ARCHITECTURE.md §7). */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order", columnList = "order_id"),
        @Index(name = "idx_payments_client", columnList = "client_user_id")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private PaymentType type;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "mlm_user_id", length = 128, updatable = false)
    private String mlmUserId;

    @Column(name = "mlm_tariff_id", updatable = false)
    private UUID mlmTariffId;

    @Column(name = "client_user_id", nullable = false, updatable = false)
    private UUID clientUserId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "provider_payment_id", length = 200)
    private String providerPaymentId;

    @Column(name = "hosted_page_url", length = 1000)
    private String hostedPageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public static Payment forOrder(UUID orderId, UUID clientUserId, long amountMinor, String currency, String provider) {
        Payment p = new Payment();
        p.type = PaymentType.ORDER;
        p.orderId = orderId;
        p.clientUserId = clientUserId;
        p.amountMinor = amountMinor;
        p.currency = currency;
        p.provider = provider;
        p.status = PaymentStatus.PENDING;
        return p;
    }

    public static Payment forMlmAccess(String mlmUserId, UUID tariffId, UUID clientUserId,
                                       long amountMinor, String currency, String provider) {
        Payment p = new Payment();
        p.type = PaymentType.MLM_ACCESS;
        p.mlmUserId = mlmUserId;
        p.mlmTariffId = tariffId;
        p.clientUserId = clientUserId;
        p.amountMinor = amountMinor;
        p.currency = currency;
        p.provider = provider;
        p.status = PaymentStatus.PENDING;
        return p;
    }

    public void attachProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }

    public void attachHostedPage(String providerPaymentId, String hostedPageUrl) {
        this.providerPaymentId = providerPaymentId;
        this.hostedPageUrl = hostedPageUrl;
    }

    public String getHostedPageUrl() {
        return hostedPageUrl;
    }

    public void markSucceeded() {
        requirePending();
        this.status = PaymentStatus.SUCCEEDED;
    }

    public void markFailed() {
        requirePending();
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        requirePending();
        this.status = PaymentStatus.CANCELLED;
    }

    /** Эквайер списал деньги уже после отмены заказа — сразу возвращаем, заказ не оплачивается. */
    public void markLateCaptureRefunded() {
        if (status != PaymentStatus.CANCELLED) {
            throw new DomainException(FinanceErrors.PAYMENT_STATUS_INVALID,
                    "payment " + id + " is " + status + ", not a late capture");
        }
        this.status = PaymentStatus.REFUNDED;
    }

    public void markRefunded() {
        if (status != PaymentStatus.SUCCEEDED) {
            throw new DomainException(FinanceErrors.PAYMENT_STATUS_INVALID,
                    "payment " + id + " is " + status + ", cannot refund");
        }
        this.status = PaymentStatus.REFUNDED;
    }

    private void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new DomainException(FinanceErrors.PAYMENT_STATUS_INVALID,
                    "payment " + id + " is already " + status);
        }
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public PaymentType getType() {
        return type;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getMlmUserId() {
        return mlmUserId;
    }

    public UUID getMlmTariffId() {
        return mlmTariffId;
    }

    public UUID getClientUserId() {
        return clientUserId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
