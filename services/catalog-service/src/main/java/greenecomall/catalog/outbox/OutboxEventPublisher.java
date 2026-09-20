package greenecomall.catalog.outbox;

import greenecomall.catalog.domain.OutboxMessage;
import greenecomall.catalog.repo.OutboxRepository;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Реализация {@link DomainEventPublisher} через transactional outbox: событие сохраняется
 * в таблицу {@code outbox} в той же транзакции, что и бизнес-данные. Досылку в Kafka делает
 * {@link OutboxRelay}.
 */
@Component
public class OutboxEventPublisher implements DomainEventPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String topic, String key, EventEnvelope<?> event) {
        try {
            outbox.save(new OutboxMessage(
                    topic, key, event.eventType(), objectMapper.writeValueAsString(event)));
        } catch (JacksonException e) {
            throw new IllegalStateException("cannot serialise event " + event.eventType(), e);
        }
    }
}
