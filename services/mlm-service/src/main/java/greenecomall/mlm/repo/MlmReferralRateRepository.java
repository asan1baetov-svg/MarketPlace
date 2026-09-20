package greenecomall.mlm.repo;

import greenecomall.mlm.domain.MlmReferralRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MlmReferralRateRepository extends JpaRepository<MlmReferralRate, UUID> {

    List<MlmReferralRate> findByTariffIdOrderByLevelAsc(UUID tariffId);

    Optional<MlmReferralRate> findByTariffIdAndLevel(UUID tariffId, int level);
}
