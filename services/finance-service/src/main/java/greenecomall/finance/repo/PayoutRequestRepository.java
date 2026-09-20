package greenecomall.finance.repo;

import greenecomall.finance.domain.PayoutRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    Page<PayoutRequest> findByStatusOrderByCreatedAtAsc(PayoutRequest.Status status, Pageable pageable);

    Page<PayoutRequest> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    @Query("select p.id from PayoutRequest p where p.auto = true and p.status = :status and p.nextAttemptAt <= :now order by p.nextAttemptAt")
    List<UUID> findDueAutoIds(@Param("status") PayoutRequest.Status status, @Param("now") Instant now, Pageable pageable);

    List<PayoutRequest> findByRequestedByAndStatus(String requestedBy, PayoutRequest.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayoutRequest> findWithLockById(UUID id);
}
