package greenecomall.catalog.repo;

import greenecomall.catalog.domain.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, UUID> {

    List<DeliveryZone> findByCityId(UUID cityId);

    boolean existsByCityId(UUID cityId);
}
