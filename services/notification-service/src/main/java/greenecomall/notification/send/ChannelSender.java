package greenecomall.notification.send;

import greenecomall.notification.domain.Channel;

import java.util.UUID;

/**
 * Шлюз одного канала доставки (FCM/APNs, SMS-провайдер, Telegram Bot API).
 * {@code userId == null} — адресат администраторы платформы.
 */
public interface ChannelSender {

    Channel channel();

    /** @return ссылка на отправку у провайдера (message id) */
    String send(UUID userId, String subject, String text);
}
