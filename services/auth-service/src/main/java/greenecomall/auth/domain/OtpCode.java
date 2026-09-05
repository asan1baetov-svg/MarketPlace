package greenecomall.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Одноразовый код подтверждения. Хранится только хэш кода.
 * Ключ поиска — пара (target, purpose); актуальным считается последний невыгоревший код.
 */
@Entity
@Table(name = "otp_codes")
public class OtpCode {

    /** Сколько неверных попыток ввода допускается до того, как код сгорает. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OtpChannel channel;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpCode() {
    }

    public OtpCode(String target, OtpChannel channel, String codeHash, OtpPurpose purpose, Instant expiresAt) {
        this.target = target;
        this.channel = channel;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public void registerFailedAttempt() {
        this.attempts++;
    }

    public void markConsumed(Instant now) {
        this.consumedAt = now;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean attemptsExhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    public boolean isVerifiable(Instant now) {
        return !isConsumed() && !isExpired(now) && !attemptsExhausted();
    }

    public UUID getId() {
        return id;
    }

    public String getTarget() {
        return target;
    }

    public OtpChannel getChannel() {
        return channel;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
