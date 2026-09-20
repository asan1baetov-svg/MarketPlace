package greenecomall.order.outbox;

import greenecomall.order.domain.OutboxMessage;
import greenecomall.order.repo.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Досылает неотправленные строки outbox в Kafka. Порядок — по времени создания.
 * Ошибка отправки одной строки не блокирует остальные: она будет повторена на следующем тике.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int SEND_TIMEOUT_SECONDS = 5;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${order.outbox-poll-interval:2s}")
    @Transactional
    public void flush() {
        List<OutboxMessage> batch = outbox.findTop100BySentAtIsNullOrderByCreatedAtAsc();
        for (OutboxMessage message : batch) {
            try {
                kafka.send(message.getTopic(), message.getPartitionKey(), message.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                message.markSent(clock.instant());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("outbox: failed to publish {} id={}, will retry",
                        message.getEventType(), message.getId(), e);
            }
        }
    }
}
