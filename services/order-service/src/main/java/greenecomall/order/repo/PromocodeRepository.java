package greenecomall.order.repo;

import greenecomall.order.domain.Promocode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromocodeRepository extends JpaRepository<Promocode, UUID> {

    Optional<Promocode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
