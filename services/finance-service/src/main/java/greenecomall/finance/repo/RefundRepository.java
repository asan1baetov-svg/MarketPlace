package greenecomall.finance.repo;

import greenecomall.finance.domain.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByPaymentId(UUID paymentId);

    Page<Refund> findByStatusOrderByCreatedAtAsc(Refund.Status status, Pageable pageable);
}
