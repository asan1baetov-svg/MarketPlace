package greenecomall.common.events.payload;

import java.util.Map;
import java.util.UUID;

/**
 * Payload-контракты командного топика {@code notifications.commands}.
 * Любой сервис может попросить notification-service отправить уведомление.
 */
public final class NotificationCommands {

    public record SendNotification(
            UUID userId,
            String templateCode,
            String channel,
            Map<String, Object> payload) {
    }

    private NotificationCommands() {
    }
}
