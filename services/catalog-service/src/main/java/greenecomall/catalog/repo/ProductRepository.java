package greenecomall.catalog.repo;

import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Витрина: гео-фильтр по городу + опциональные категория/магазин, только опубликованные.
     * Ценовой фильтр (min/max) в этом MVP не делаем на уровне БД — {@code sale_price} не
     * персистится (резолвится динамически через {@code markup_rules}), фильтрация по цене
     * потребовала бы денормализованной/кэшированной колонки. Сортировка по цене — приближённая,
     * по {@code costPriceMinor} (см. {@code Pageable}).
     */
    /** Админ-поиск: товары на модерации и любые другие по статусу/магазину/городу. */
    @Query("""
            select p from Product p
            where (:status is null or p.status = :status)
              and (:shopId is null or p.shop.id = :shopId)
              and (:cityId is null or p.city.id = :cityId)
            """)
    Page<Product> adminSearch(@Param("status") ProductStatus status, @Param("shopId") UUID shopId,
                              @Param("cityId") UUID cityId, Pageable pageable);

    @Query("""
            select p from Product p
            where p.city.id = :cityId
              and p.status = greenecomall.catalog.domain.ProductStatus.PUBLISHED
              and p.shop.status = greenecomall.catalog.domain.ShopStatus.ACTIVE
              and (:categoryId is null or p.category.id = :categoryId)
              and (:shopId is null or p.shop.id = :shopId)
            """)
    Page<Product> searchStorefront(@Param("cityId") UUID cityId, @Param("categoryId") UUID categoryId,
                                    @Param("shopId") UUID shopId, Pageable pageable);

    List<Product> findByShopId(UUID shopId);

    boolean existsByCategoryId(UUID categoryId);
}
