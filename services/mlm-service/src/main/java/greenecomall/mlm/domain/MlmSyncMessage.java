package greenecomall.mlm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox обратной синхронизации во внешний MLM-бэк (не Kafka — HTTP-вызов с ретраями).
 * Пишется в одной транзакции с изменением аккаунта; {@code MlmSyncRelay} отправляет с
 * экспоненциальной задержкой. {@code id} уходит как {@code X-Idempotency-Key}.
 */
@Entity
@Table(name = "mlm_sync_outbox")
public class MlmSyncMessage {

    public enum Type {ACTIVATION_STATUS, PURCHASE_REPORTED}

    public enum Status {PENDING, SENT, FAILED}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24, updatable = false)
    private Type type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MlmSyncMessage() {
    }

    public MlmSyncMessage(Type type, String payload, Instant now) {
        this.type = type;
        this.payload = payload;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
    }

    public void markSent(Instant now) {
        this.status = Status.SENT;
        this.attempts++;
        this.lastAttemptAt = now;
        this.lastError = null;
    }

    /** Неудача: удваиваем задержку; после {@code maxAttempts} — FAILED до ручного разбора. */
    public void markFailed(String error, Instant now, Duration initialBackoff, int maxAttempts) {
        this.attempts++;
        this.lastAttemptAt = now;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
        } else {
            long factor = 1L << Math.min(attempts - 1, 16);
            this.nextAttemptAt = now.plus(initialBackoff.multipliedBy(factor));
        }
    }

    /** Ручной перезапуск из админки. */
    public void retryNow(Instant now) {
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
