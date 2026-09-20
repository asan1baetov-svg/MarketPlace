package greenecomall.catalog.repo;

import greenecomall.catalog.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CityRepository extends JpaRepository<City, UUID> {

    List<City> findByCountryId(UUID countryId);

    boolean existsByCountryId(UUID countryId);
}
