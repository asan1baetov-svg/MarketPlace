package greenecomall.notification.send;

import greenecomall.notification.config.NotificationProperties;
import greenecomall.notification.domain.Channel;
import greenecomall.notification.domain.Notification;
import greenecomall.notification.domain.NotificationTemplate;
import greenecomall.notification.domain.UserNotificationPref;
import greenecomall.notification.repo.NotificationRepository;
import greenecomall.notification.repo.NotificationTemplateRepository;
import greenecomall.notification.repo.UserNotificationPrefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Единая точка отправки: выбирает каналы по настройкам пользователя, рендерит шаблон
 * {@code code+channel+locale}, отправляет через шлюз канала и пишет журнал.
 * Ошибка шлюза не пробрасывается наружу — фиксируется как {@code FAILED}, чтобы не зацикливать консюмер Kafka.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationTemplateRepository templates;
    private final NotificationRepository notifications;
    private final UserNotificationPrefRepository prefs;
    private final Map<Channel, ChannelSender> senders = new EnumMap<>(Channel.class);
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationService(NotificationTemplateRepository templates, NotificationRepository notifications,
                               UserNotificationPrefRepository prefs, List<ChannelSender> senderBeans,
                               NotificationProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.templates = templates;
        this.notifications = notifications;
        this.prefs = prefs;
        senderBeans.forEach(s -> senders.put(s.channel(), s));
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param userId  получатель; {@code null} — администраторы
     * @param channel конкретный канал или {@code null} — по настройкам пользователя
     */
    @Transactional
    public void notify(UUID userId, String templateCode, Map<String, ?> payload, Channel channel) {
        List<Channel> channels = channel != null ? List.of(channel) : channelsFor(userId);
        for (Channel ch : channels) {
            deliver(userId, templateCode, payload, ch);
        }
    }

    private void deliver(UUID userId, String templateCode, Map<String, ?> payload, Channel channel) {
        Notification n = notifications.save(new Notification(userId, channel, templateCode,
                objectMapper.writeValueAsString(payload == null ? Map.of() : payload)));
        NotificationTemplate template = templates
                .findByCodeAndChannelAndLocale(templateCode, channel, properties.defaultLocale())
                .orElse(null);
        if (template == null) {
            n.markSkipped("no template " + templateCode + "/" + channel + "/" + properties.defaultLocale());
            log.debug("no template {} for {}", templateCode, channel);
            return;
        }
        ChannelSender sender = senders.get(channel);
        String text = TemplateRenderer.render(template.getBody(), payload);
        if (sender == null) {
            n.markFailed(text, "no sender for channel " + channel);
            return;
        }
        try {
            String ref = sender.send(userId, TemplateRenderer.render(template.getSubject(), payload), text);
            n.markSent(text, ref, Instant.now(clock));
        } catch (RuntimeException e) {
            log.warn("notification {} via {} failed", templateCode, channel, e);
            n.markFailed(text, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Channel> effectiveChannels(UUID userId) {
        return channelsFor(userId);
    }

    /** Каналы пользователя: явные настройки, иначе умолчание; для админов — Telegram. */
    List<Channel> channelsFor(UUID userId) {
        if (userId == null) {
            return List.of(Channel.TELEGRAM);
        }
        List<UserNotificationPref> own = prefs.findByUserId(userId);
        if (own.isEmpty()) {
            return properties.defaultChannels();
        }
        return own.stream().filter(UserNotificationPref::isEnabled).map(UserNotificationPref::getChannel).toList();
    }
}
