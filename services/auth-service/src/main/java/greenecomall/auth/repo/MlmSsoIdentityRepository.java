package greenecomall.auth.repo;

import greenecomall.auth.domain.MlmSsoIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MlmSsoIdentityRepository extends JpaRepository<MlmSsoIdentity, UUID> {

    Optional<MlmSsoIdentity> findByMlmUserId(String mlmUserId);
}
