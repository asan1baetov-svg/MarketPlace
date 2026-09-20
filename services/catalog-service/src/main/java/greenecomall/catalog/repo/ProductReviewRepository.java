package greenecomall.catalog.repo;

import greenecomall.catalog.domain.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {

    Optional<ProductReview> findByProductIdAndAuthorUserId(UUID productId, UUID authorUserId);

    Page<ProductReview> findByProductIdAndHiddenFalseOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    Page<ProductReview> findByShopIdOrderByCreatedAtDesc(UUID shopId, Pageable pageable);

    Page<ProductReview> findByHiddenOrderByCreatedAtDesc(boolean hidden, Pageable pageable);

    /** [средняя оценка, количество] по видимым отзывам товара. */
    @Query("select avg(r.rating), count(r) from ProductReview r where r.productId = :productId and r.hidden = false")
    List<Object[]> aggregateForProduct(@Param("productId") UUID productId);

    @Query("select avg(r.rating), count(r) from ProductReview r where r.shopId = :shopId and r.hidden = false")
    List<Object[]> aggregateForShop(@Param("shopId") UUID shopId);

    /** [оценка, количество] — распределение звёзд для карточки товара. */
    @Query("""
            select r.rating, count(r) from ProductReview r
            where r.productId = :productId and r.hidden = false group by r.rating""")
    List<Object[]> distributionForProduct(@Param("productId") UUID productId);
}
