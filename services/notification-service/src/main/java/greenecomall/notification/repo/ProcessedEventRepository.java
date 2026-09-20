package greenecomall.notification.repo;

import greenecomall.notification.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    boolean existsByEventIdAndConsumer(java.util.UUID eventId, String consumer);
}
