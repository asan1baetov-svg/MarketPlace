package greenecomall.mlm.repo;

import greenecomall.mlm.domain.MlmTariff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MlmTariffRepository extends JpaRepository<MlmTariff, UUID> {

    Optional<MlmTariff> findFirstByIsDefaultTrueAndActiveTrue();
}
