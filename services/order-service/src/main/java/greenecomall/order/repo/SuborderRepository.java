package greenecomall.order.repo;

import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SuborderRepository extends JpaRepository<Suborder, UUID> {

    List<Suborder> findByOrderId(UUID orderId);

    Page<Suborder> findByShopIdOrderByCreatedAtDesc(UUID shopId, Pageable pageable);

    Page<Suborder> findByShopIdAndStatusOrderByCreatedAtDesc(UUID shopId, SuborderStatus status, Pageable pageable);
}
