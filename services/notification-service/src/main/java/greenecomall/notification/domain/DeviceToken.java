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

import java.time.Instant;
import java.util.UUID;

/** Push-токен устройства пользователя (FCM/APNs/Web Push). */
@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    public enum Platform {IOS, ANDROID, WEB}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Platform platform;

    @Column(nullable = false, unique = true, length = 500, updatable = false)
    private String token;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DeviceToken() {
    }

    public DeviceToken(UUID userId, Platform platform, String token) {
        this.userId = userId;
        this.platform = platform;
        this.token = token;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getToken() {
        return token;
    }
}
