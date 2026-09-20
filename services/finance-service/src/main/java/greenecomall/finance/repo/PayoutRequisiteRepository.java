package greenecomall.finance.repo;

import greenecomall.finance.domain.PayoutRequisite;
import greenecomall.finance.domain.WalletOwnerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRequisiteRepository extends JpaRepository<PayoutRequisite, UUID> {

    List<PayoutRequisite> findByOwnerTypeAndOwnerRefOrderByCreatedAtAsc(WalletOwnerType ownerType, String ownerRef);

    Optional<PayoutRequisite> findFirstByOwnerTypeAndOwnerRefAndStatusOrderByCreatedAtAsc(
            WalletOwnerType ownerType, String ownerRef, PayoutRequisite.Status status);

    Page<PayoutRequisite> findByStatusOrderByCreatedAtAsc(PayoutRequisite.Status status, Pageable pageable);
}
