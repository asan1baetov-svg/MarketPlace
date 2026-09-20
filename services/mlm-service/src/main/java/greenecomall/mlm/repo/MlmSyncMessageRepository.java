package greenecomall.mlm.repo;

import greenecomall.mlm.domain.MlmSyncMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MlmSyncMessageRepository extends JpaRepository<MlmSyncMessage, UUID> {

    List<MlmSyncMessage> findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            MlmSyncMessage.Status status, Instant now);

    Page<MlmSyncMessage> findByStatusOrderByCreatedAtDesc(MlmSyncMessage.Status status, Pageable pageable);
}
