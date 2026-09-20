package greenecomall.mlm.repo;

import greenecomall.mlm.domain.AccessStatus;
import greenecomall.mlm.domain.MlmAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MlmAccountRepository extends JpaRepository<MlmAccount, UUID> {

    Optional<MlmAccount> findByMlmUserId(String mlmUserId);

    Optional<MlmAccount> findByUserId(UUID userId);

    List<MlmAccount> findByAccessStatus(AccessStatus status);

    Page<MlmAccount> findByAccessStatusOrderByCreatedAtDesc(AccessStatus status, Pageable pageable);

    Page<MlmAccount> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByAccessStatus(AccessStatus status);

    List<MlmAccount> findByUplineMlmUserId(String uplineMlmUserId);
}
