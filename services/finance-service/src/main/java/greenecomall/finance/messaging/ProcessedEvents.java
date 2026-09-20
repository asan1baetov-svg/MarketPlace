package greenecomall.finance.messaging;

import greenecomall.finance.domain.ProcessedEvent;
import greenecomall.finance.repo.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Идемпотентность консюмеров: повторная доставка того же {@code eventId} игнорируется. */
@Component
public class ProcessedEvents {

    private final ProcessedEventRepository repository;

    public ProcessedEvents(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean alreadyHandled(UUID eventId, String consumer) {
        return repository.existsByEventIdAndConsumer(eventId, consumer);
    }

    @Transactional
    public void markHandled(UUID eventId, String consumer) {
        try {
            repository.saveAndFlush(new ProcessedEvent(eventId, consumer));
        } catch (DataIntegrityViolationException ignored) {
            // конкурентная доставка уже записала отметку
        }
    }
}
