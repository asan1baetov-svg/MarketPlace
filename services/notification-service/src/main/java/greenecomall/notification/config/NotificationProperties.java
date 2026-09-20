package greenecomall.notification.config;

import greenecomall.notification.domain.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param defaultLocale локаль шаблонов, если у пользователя не задана своя
 * @param defaultChannels каналы по умолчанию для пользователя без явных настроек
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String defaultLocale, List<Channel> defaultChannels) {

    public NotificationProperties {
        if (defaultLocale == null || defaultLocale.isBlank()) {
            defaultLocale = "ru";
        }
        if (defaultChannels == null || defaultChannels.isEmpty()) {
            defaultChannels = List.of(Channel.PUSH);
        }
    }
}
