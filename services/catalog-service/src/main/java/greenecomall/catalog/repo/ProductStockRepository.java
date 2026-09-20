package greenecomall.catalog.repo;

import greenecomall.catalog.domain.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductStockRepository extends JpaRepository<ProductStock, UUID> {

    Optional<ProductStock> findByProductId(UUID productId);

    /**
     * Условный резерв: проходит, только если хватает свободного остатка. Возвращает число
     * обновлённых строк (0 — недостаточно остатка или товара нет).
     */
    @Modifying
    @Query("""
            update ProductStock s set s.reservedQuantity = s.reservedQuantity + :qty
            where s.productId = :productId and s.quantity - s.reservedQuantity >= :qty
            """)
    int reserve(@Param("productId") UUID productId, @Param("qty") int qty);

    @Modifying
    @Query("""
            update ProductStock s set s.reservedQuantity = greatest(s.reservedQuantity - :qty, 0)
            where s.productId = :productId
            """)
    int release(@Param("productId") UUID productId, @Param("qty") int qty);

    @Modifying
    @Query("""
            update ProductStock s
            set s.quantity = greatest(s.quantity - :qty, 0),
                s.reservedQuantity = greatest(s.reservedQuantity - :qty, 0)
            where s.productId = :productId
            """)
    int commit(@Param("productId") UUID productId, @Param("qty") int qty);
}
