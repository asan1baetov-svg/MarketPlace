package greenecomall.notification.messaging;

import greenecomall.notification.domain.ProcessedEvent;
import greenecomall.notification.repo.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Идемпотентность консюмеров: повторная доставка того же {@code eventId} тем же консюмером
 * не приводит к повторной обработке (см. docs/ARCHITECTURE.md §4.1).
 */
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

    /**
     * Помечает событие обработанным. Гонка двух доставок ловится уникальным ключом —
     * второе включение просто игнорируется.
     */
    @Transactional
    public void markHandled(UUID eventId, String consumer) {
        try {
            repository.saveAndFlush(new ProcessedEvent(eventId, consumer));
        } catch (DataIntegrityViolationException ignored) {
            // конкурентная доставка уже записала отметку — это нормально
        }
    }
}
