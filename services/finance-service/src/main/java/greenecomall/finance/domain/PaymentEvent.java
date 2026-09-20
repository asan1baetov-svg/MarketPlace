package greenecomall.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Лог входящего webhook эквайринга: сырой payload + результат проверки подписи. Уникальность
 * {@code providerEventId} обеспечивает идемпотентность обработки (docs/ARCHITECTURE.md §7.1, R3).
 */
@Entity
@Table(name = "payment_events", indexes = @Index(name = "idx_payment_events_payment", columnList = "payment_id"))
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "provider_event_id", nullable = false, unique = true, updatable = false, length = 200)
    private String providerEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, updatable = false)
    private String rawPayload;

    @Column(name = "signature_valid", nullable = false, updatable = false)
    private boolean signatureValid;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected PaymentEvent() {
    }

    public PaymentEvent(UUID paymentId, String providerEventId, String rawPayload, boolean signatureValid) {
        this.paymentId = paymentId;
        this.providerEventId = providerEventId;
        this.rawPayload = rawPayload;
        this.signatureValid = signatureValid;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public boolean isSignatureValid() {
        return signatureValid;
    }
}
