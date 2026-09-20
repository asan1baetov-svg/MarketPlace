package greenecomall.order.repo;

import greenecomall.order.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<OrderStatusHistory> findBySuborderIdOrderByCreatedAtAsc(UUID suborderId);
}
