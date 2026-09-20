package greenecomall.courier.repo;

import greenecomall.courier.domain.CourierEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CourierEarningRepository extends JpaRepository<CourierEarning, UUID> {

    boolean existsBySuborderId(UUID suborderId);

    List<CourierEarning> findByCourierIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID courierId, Instant from, Instant to);

    @Query("""
            select coalesce(sum(e.amountMinor), 0) from CourierEarning e
            where e.courierId = :courierId and e.createdAt between :from and :to
            """)
    long sumForPeriod(@Param("courierId") UUID courierId, @Param("from") Instant from, @Param("to") Instant to);
}
