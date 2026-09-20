package greenecomall.mlm.integration;

import greenecomall.mlm.config.MlmProperties;
import greenecomall.mlm.domain.MlmSyncMessage;
import greenecomall.mlm.repo.MlmSyncMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Отправляет накопленные сообщения во внешний MLM-бэк. Пока {@code mlm.sync.base-url} не задан,
 * ничего не делает — сообщения ждут в outbox и уйдут после настройки.
 */
@Component
public class MlmSyncRelay {

    private static final Logger log = LoggerFactory.getLogger(MlmSyncRelay.class);

    private final MlmSyncMessageRepository messages;
    private final MlmSyncClient client;
    private final MlmProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MlmSyncRelay(MlmSyncMessageRepository messages, MlmSyncClient client, MlmProperties properties,
                        ObjectMapper objectMapper, Clock clock) {
        this.messages = messages;
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mlm.sync.poll-interval:10s}")
    @Transactional
    public int flush() {
        if (!properties.sync().enabled()) {
            return 0;
        }
        Instant now = clock.instant();
        List<MlmSyncMessage> batch = messages.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                MlmSyncMessage.Status.PENDING, now.plusMillis(1));
        for (MlmSyncMessage message : batch) {
            try {
                String key = message.getId().toString();
                switch (message.getType()) {
                    case ACTIVATION_STATUS -> client.reportActivationStatus(
                            objectMapper.readValue(message.getPayload(), MlmSyncClient.ActivationStatus.class), key);
                    case PURCHASE_REPORTED -> client.reportPurchase(
                            objectMapper.readValue(message.getPayload(), MlmSyncClient.Purchase.class), key);
                }
                message.markSent(now);
            } catch (RuntimeException e) {
                message.markFailed(e.getMessage(), now, properties.sync().initialBackoff(), properties.sync().maxAttempts());
                log.warn("mlm sync {} id={} attempt {} failed: {}",
                        message.getType(), message.getId(), message.getAttempts(), e.getMessage());
            }
        }
        return batch.size();
    }
}
