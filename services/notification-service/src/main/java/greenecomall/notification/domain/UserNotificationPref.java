package greenecomall.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Включён ли канал у пользователя. Нет строки — действуют каналы по умолчанию. */
@Entity
@Table(name = "user_notification_prefs")
@IdClass(UserNotificationPref.Key.class)
public class UserNotificationPref {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Channel channel;

    @Column(nullable = false)
    private boolean enabled;

    protected UserNotificationPref() {
    }

    public UserNotificationPref(UUID userId, Channel channel, boolean enabled) {
        this.userId = userId;
        this.channel = channel;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getUserId() {
        return userId;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static class Key implements Serializable {
        private UUID userId;
        private Channel channel;

        public Key() {
        }

        public Key(UUID userId, Channel channel) {
            this.userId = userId;
            this.channel = channel;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && channel == k.channel;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, channel);
        }
    }
}
