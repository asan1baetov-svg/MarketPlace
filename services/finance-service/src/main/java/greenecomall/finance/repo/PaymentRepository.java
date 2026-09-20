package greenecomall.finance.repo;

import greenecomall.finance.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    boolean existsByOrderId(UUID orderId);
}
