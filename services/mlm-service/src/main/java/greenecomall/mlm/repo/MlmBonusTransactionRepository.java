package greenecomall.mlm.repo;

import greenecomall.mlm.domain.MlmBonusTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MlmBonusTransactionRepository extends JpaRepository<MlmBonusTransaction, UUID> {

    List<MlmBonusTransaction> findByMlmAccountIdOrderByCreatedAtDesc(UUID mlmAccountId);

    List<MlmBonusTransaction> findBySourceRef(String sourceRef);

    boolean existsBySourceRefAndMlmAccountId(String sourceRef, UUID mlmAccountId);
}
