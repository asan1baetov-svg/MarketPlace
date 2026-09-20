package greenecomall.notification.domain;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Журнал отправок. {@code userId == null} — служебное уведомление администраторам
 * (нет курьеров в городе, новая заявка на вывод и т.п.).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Status {QUEUED, SENT, FAILED, SKIPPED}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Channel channel;

    @Column(name = "template_code", nullable = false, length = 64, updatable = false)
    private String templateCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "rendered_text", length = 2000)
    private String renderedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.QUEUED;

    @Column(name = "provider_ref", length = 200)
    private String providerRef;

    @Column(length = 1000)
    private String error;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {
    }

    public Notification(UUID userId, Channel channel, String templateCode, String payload) {
        this.userId = userId;
        this.channel = channel;
        this.templateCode = templateCode;
        this.payload = payload;
        this.status = Status.QUEUED;
    }

    public void markSent(String renderedText, String providerRef, Instant now) {
        this.renderedText = renderedText;
        this.providerRef = providerRef;
        this.status = Status.SENT;
        this.sentAt = now;
    }

    public void markFailed(String renderedText, String error) {
        this.renderedText = renderedText;
        this.status = Status.FAILED;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }

    public void markSkipped(String reason) {
        this.status = Status.SKIPPED;
        this.error = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getRenderedText() {
        return renderedText;
    }

    public Status getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
