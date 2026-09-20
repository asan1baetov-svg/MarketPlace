package greenecomall.finance.repo;

import greenecomall.finance.domain.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    boolean existsByProviderEventId(String providerEventId);
}
