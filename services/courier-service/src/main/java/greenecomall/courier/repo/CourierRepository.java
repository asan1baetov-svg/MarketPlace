package greenecomall.courier.repo;

import greenecomall.courier.domain.Courier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {

    Optional<Courier> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @Query("""
            select c from Courier c
            where c.cityId = :cityId
              and c.moderationStatus = greenecomall.courier.domain.Courier.Moderation.APPROVED
              and c.status = greenecomall.courier.domain.Courier.Status.ACTIVE
            """)
    List<Courier> findDispatchableInCity(@Param("cityId") UUID cityId);

    @Query("""
            select c from Courier c
            where (:cityId is null or c.cityId = :cityId)
              and (:moderation is null or c.moderationStatus = :moderation)
            order by c.createdAt desc
            """)
    Page<Courier> search(@Param("cityId") UUID cityId,
                         @Param("moderation") Courier.Moderation moderation,
                         Pageable pageable);
}
