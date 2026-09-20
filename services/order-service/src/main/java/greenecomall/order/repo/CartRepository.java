package greenecomall.order.repo;

import greenecomall.order.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByClientUserId(UUID clientUserId);
}
