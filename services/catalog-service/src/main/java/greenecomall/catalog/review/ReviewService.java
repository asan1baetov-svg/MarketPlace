package greenecomall.catalog.review;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductReview;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.catalog.repo.ProductReviewRepository;
import greenecomall.catalog.repo.ShopRepository;
import greenecomall.common.domain.DomainException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Отзывы о товарах. Правила как на маркетплейсах: отзыв — только от покупателя, получившего товар,
 * один на товар (повтор — правка), магазин отвечает на отзыв, админ скрывает нарушающие правила.
 * Рейтинг товара и магазина пересчитывается сразу по видимым отзывам.
 */
@Service
public class ReviewService {

    private final ProductReviewRepository reviews;
    private final ProductRepository products;
    private final ShopRepository shops;
    private final PurchaseChecker purchases;
    private final Clock clock;

    public ReviewService(ProductReviewRepository reviews, ProductRepository products, ShopRepository shops,
                         PurchaseChecker purchases, Clock clock) {
        this.reviews = reviews;
        this.products = products;
        this.shops = shops;
        this.purchases = purchases;
        this.clock = clock;
    }

    /** Оставить или исправить свой отзыв. */
    @Transactional
    public ProductReview upsert(UUID productId, UUID authorUserId, int rating, String text) {
        Product product = requireProduct(productId);
        Instant now = Instant.now(clock);
        ProductReview review = reviews.findByProductIdAndAuthorUserId(productId, authorUserId).orElse(null);
        if (review == null) {
            if (!purchases.hasReceived(authorUserId, productId)) {
                throw new DomainException(CatalogErrors.REVIEW_NOT_ALLOWED,
                        "only a customer who received the product can review it");
            }
            review = reviews.save(new ProductReview(productId, product.getShop().getId(), authorUserId, rating,
                    blankToNull(text), now));
        } else {
            review.edit(rating, blankToNull(text), now);
        }
        recalculate(product);
        return review;
    }

    @Transactional
    public void deleteOwn(UUID productId, UUID authorUserId) {
        reviews.findByProductIdAndAuthorUserId(productId, authorUserId).ifPresent(review -> {
            reviews.delete(review);
            reviews.flush();
            recalculate(requireProduct(productId));
        });
    }

    /** Ответ магазина. {@code shopOwnerUserId == null} — админ (без проверки владения). */
    @Transactional
    public ProductReview reply(UUID reviewId, UUID shopOwnerUserId, String text) {
        ProductReview review = require(reviewId);
        if (shopOwnerUserId != null) {
            Shop shop = shops.findById(review.getShopId()).orElseThrow();
            if (!shop.isOwnedBy(shopOwnerUserId)) {
                throw new DomainException(CatalogErrors.REVIEW_NOT_ALLOWED, "only the shop owner can reply");
            }
        }
        review.reply(text, Instant.now(clock));
        return review;
    }

    @Transactional
    public ProductReview setHidden(UUID reviewId, boolean hidden) {
        ProductReview review = require(reviewId);
        review.setHidden(hidden);
        reviews.flush();
        recalculate(requireProduct(review.getProductId()));
        return review;
    }

    @Transactional(readOnly = true)
    public Page<ProductReview> visibleForProduct(UUID productId, Pageable pageable) {
        return reviews.findByProductIdAndHiddenFalseOrderByCreatedAtDesc(productId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductReview> forShop(UUID shopId, Pageable pageable) {
        return reviews.findByShopIdOrderByCreatedAtDesc(shopId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductReview> byHidden(boolean hidden, Pageable pageable) {
        return reviews.findByHiddenOrderByCreatedAtDesc(hidden, pageable);
    }

    /** Средняя оценка, количество и распределение по звёздам (1..5, нули включены). */
    @Transactional(readOnly = true)
    public Summary summary(UUID productId) {
        Product product = requireProduct(productId);
        Map<Integer, Long> distribution = new TreeMap<>();
        for (int stars = 1; stars <= 5; stars++) {
            distribution.put(stars, 0L);
        }
        for (Object[] row : reviews.distributionForProduct(productId)) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return new Summary(product.getRating(), product.getReviewsCount(), distribution);
    }

    @Transactional(readOnly = true)
    public Shop shopOf(ProductReview review) {
        return shops.findById(review.getShopId()).orElseThrow();
    }

    private void recalculate(Product product) {
        Aggregate p = aggregate(reviews.aggregateForProduct(product.getId()));
        product.applyRating(p.average(), p.count());
        Shop shop = product.getShop();
        Aggregate s = aggregate(reviews.aggregateForShop(shop.getId()));
        shop.applyRating(s.average(), s.count());
    }

    private static Aggregate aggregate(List<Object[]> rows) {
        Object[] row = rows.isEmpty() ? new Object[]{null, 0L} : rows.getFirst();
        int count = row[1] == null ? 0 : ((Number) row[1]).intValue();
        BigDecimal average = row[0] == null || count == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(((Number) row[0]).doubleValue()).setScale(1, RoundingMode.HALF_UP);
        return new Aggregate(average, count);
    }

    private ProductReview require(UUID reviewId) {
        return reviews.findById(reviewId)
                .orElseThrow(() -> new DomainException(CatalogErrors.REVIEW_NOT_FOUND, "review not found: " + reviewId));
    }

    private Product requireProduct(UUID productId) {
        return products.findById(productId)
                .orElseThrow(() -> new DomainException(CatalogErrors.PRODUCT_NOT_FOUND, "product not found: " + productId));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private record Aggregate(BigDecimal average, int count) {
    }

    public record Summary(BigDecimal average, int count, Map<Integer, Long> distribution) {
    }
}
