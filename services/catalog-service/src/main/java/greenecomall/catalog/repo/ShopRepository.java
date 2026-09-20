package greenecomall.catalog.repo;

import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.domain.ShopStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, UUID> {

    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);

    Page<Shop> findByOwnerUserId(UUID ownerUserId, Pageable pageable);
}
