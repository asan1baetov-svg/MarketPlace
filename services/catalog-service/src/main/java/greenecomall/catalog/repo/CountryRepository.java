package greenecomall.catalog.repo;

import greenecomall.catalog.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CountryRepository extends JpaRepository<Country, UUID> {

    boolean existsByIsoCode(String isoCode);

    boolean existsByIsoCodeAndIdNot(String isoCode, UUID id);
}
