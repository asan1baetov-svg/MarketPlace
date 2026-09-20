package greenecomall.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Шаблон уведомления по {@code code + channel + locale}; плейсхолдеры вида {@code {{status}}}. */
@Entity
@Table(name = "notification_templates", uniqueConstraints =
        @UniqueConstraint(name = "uq_templates_code_channel_locale", columnNames = {"code", "channel", "locale"}))
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    @Column(nullable = false, length = 8)
    private String locale;

    @Column(length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;

    protected NotificationTemplate() {
    }

    public NotificationTemplate(String code, Channel channel, String locale, String subject, String body) {
        this.code = code;
        this.channel = channel;
        this.locale = locale;
        this.subject = subject;
        this.body = body;
    }

    public void update(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getLocale() {
        return locale;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }
}
