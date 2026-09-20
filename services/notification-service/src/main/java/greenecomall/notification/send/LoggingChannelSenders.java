package greenecomall.notification.send;

import greenecomall.notification.domain.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * MVP-шлюзы: пишут уведомление в лог (docs/ARCHITECTURE.md §9 этап 7 — «для начала лог»).
 * Реальный провайдер канала = отдельный бин {@link ChannelSender} с тем же {@link Channel},
 * который заменяет логирующий (например, через {@code @ConditionalOnProperty}).
 */
@Configuration
public class LoggingChannelSenders {

    private static final Logger log = LoggerFactory.getLogger("notifications.outbound");

    @Bean
    public ChannelSender pushSender() {
        return logging(Channel.PUSH);
    }

    @Bean
    public ChannelSender smsSender() {
        return logging(Channel.SMS);
    }

    @Bean
    public ChannelSender telegramSender() {
        return logging(Channel.TELEGRAM);
    }

    private static ChannelSender logging(Channel channel) {
        return new ChannelSender() {
            @Override
            public Channel channel() {
                return channel;
            }

            @Override
            public String send(UUID userId, String subject, String text) {
                String ref = "log-" + UUID.randomUUID();
                log.info("[{}] to={} subject={} text={} ref={}",
                        channel, userId == null ? "ADMINS" : userId, subject, text, ref);
                return ref;
            }
        };
    }
}
