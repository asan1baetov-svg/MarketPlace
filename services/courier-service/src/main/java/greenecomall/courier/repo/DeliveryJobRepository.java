package greenecomall.courier.repo;

import greenecomall.courier.domain.DeliveryJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, UUID> {

    List<DeliveryJob> findByOrderId(UUID orderId);

    List<DeliveryJob> findByStatusIn(Collection<DeliveryJob.Status> statuses);

    List<DeliveryJob> findByStatusAndOfferExpiresAtBefore(DeliveryJob.Status status, Instant cutoff);

    Page<DeliveryJob> findByCourierIdOrderByCreatedAtDesc(UUID courierId, Pageable pageable);

    Page<DeliveryJob> findByCourierIdAndStatusInOrderByCreatedAtDesc(
            UUID courierId, Collection<DeliveryJob.Status> statuses, Pageable pageable);

    /** Текущая загрузка курьера — число незавершённых доставок на нём. */
    @Query("""
            select count(j) from DeliveryJob j
            where j.courierId = :courierId and j.status in :statuses
            """)
    long countActive(@Param("courierId") UUID courierId,
                     @Param("statuses") Collection<DeliveryJob.Status> statuses);
}
