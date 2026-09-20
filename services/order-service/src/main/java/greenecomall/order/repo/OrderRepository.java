package greenecomall.order.repo;

import greenecomall.order.domain.Order;
import greenecomall.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByClientUserIdOrderByCreatedAtDesc(UUID clientUserId, Pageable pageable);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant cutoff);

    @Query("""
            select o from Order o
            where (:status is null or o.status = :status)
              and (:cityId is null or o.cityId = :cityId)
              and (:clientId is null or o.clientUserId = :clientId)
            order by o.createdAt desc
            """)
    Page<Order> search(@Param("status") OrderStatus status,
                       @Param("cityId") UUID cityId,
                       @Param("clientId") UUID clientId,
                       Pageable pageable);
}
