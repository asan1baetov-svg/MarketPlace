package greenecomall.catalog.repo;

import greenecomall.catalog.domain.ProductImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortAsc(UUID productId);

    long countByProductId(UUID productId);

    long countByProductIdAndKind(UUID productId, ProductImage.Kind kind);

    @Query("select coalesce(max(i.sort), -10) from ProductImage i where i.product.id = :productId")
    int maxSort(@Param("productId") UUID productId);

    /** Видимые покупателю фото сразу для страницы витрины — один запрос вместо N. */
    default List<ProductImage> findVisibleForProducts(Collection<UUID> productIds) {
        return findForProducts(productIds, ProductImage.Status.READY);
    }

    @Query("""
            select i from ProductImage i
            where i.product.id in :productIds and i.published = true and i.status = :ready and i.url is not null
            order by i.sort asc""")
    List<ProductImage> findForProducts(@Param("productIds") Collection<UUID> productIds,
                                       @Param("ready") ProductImage.Status ready);

    @Query("select i.id from ProductImage i where i.status = :status order by i.createdAt asc")
    List<UUID> findIdsByStatus(@Param("status") ProductImage.Status status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProductImage i where i.id = :id")
    Optional<ProductImage> findWithLockById(@Param("id") UUID id);

    List<ProductImage> findBySourceImageId(UUID sourceImageId);
}
