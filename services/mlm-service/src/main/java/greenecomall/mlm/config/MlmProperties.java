package greenecomall.mlm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Настройки mlm-service.
 *
 * @param localBonusesEnabled считать ли реферальные бонусы на стороне маркетплейса. По умолчанию
 *                            {@code false}: во внешнем MLM-бэке уже есть своё дерево и начисления,
 *                            маркетплейс только сообщает о покупках (иначе бонусы задвоятся)
 * @param reminderDaysBeforeDeadline за сколько дней до дедлайна активации напоминать клиенту
 * @param sync параметры обратной синхронизации во внешний MLM-бэк
 */
@ConfigurationProperties(prefix = "mlm")
public record MlmProperties(boolean localBonusesEnabled, List<Integer> reminderDaysBeforeDeadline, Sync sync) {

    public MlmProperties {
        if (reminderDaysBeforeDeadline == null || reminderDaysBeforeDeadline.isEmpty()) {
            reminderDaysBeforeDeadline = List.of(3, 1);
        }
        if (sync == null) {
            sync = new Sync(null, null, 0, null, null);
        }
    }

    /**
     * @param baseUrl базовый URL внешнего MLM-бэка; пусто — синхронизация выключена (события копятся в outbox)
     * @param sharedSecret секрет HMAC-подписи тел обратных вызовов и проверки входящих webhook
     * @param maxAttempts после стольких неудач запись помечается FAILED и ждёт ручного разбора
     * @param initialBackoff первая задержка ретрая, далее удваивается
     * @param pollInterval период работы публикатора
     */
    public record Sync(String baseUrl, String sharedSecret, int maxAttempts, Duration initialBackoff,
                       Duration pollInterval) {

        public Sync {
            if (sharedSecret == null || sharedSecret.isBlank()) {
                sharedSecret = "local-dev-mlm-shared-secret-change-me";
            }
            if (maxAttempts <= 0) {
                maxAttempts = 10;
            }
            if (initialBackoff == null) {
                initialBackoff = Duration.ofSeconds(30);
            }
            if (pollInterval == null) {
                pollInterval = Duration.ofSeconds(10);
            }
        }

        public boolean enabled() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }
}
