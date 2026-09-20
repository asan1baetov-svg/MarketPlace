package greenecomall.catalog.review;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.Category;
import greenecomall.catalog.domain.City;
import greenecomall.catalog.domain.Country;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductReview;
import greenecomall.catalog.domain.ProductUnit;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.catalog.repo.ProductReviewRepository;
import greenecomall.catalog.repo.ShopRepository;
import greenecomall.common.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ProductReviewRepository reviews;
    @Mock private ProductRepository products;
    @Mock private ShopRepository shops;
    @Mock private PurchaseChecker purchases;

    private ReviewService service;
    private Product product;
    private Shop shop;
    private final UUID buyer = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviews, products, shops, purchases, Clock.systemUTC());
        Country country = new Country("Kyrgyzstan", "KG");
        City city = new City(country, "Bishkek", null, null, null);
        shop = new Shop(owner, "Eco Shop", null, country, city);
        ReflectionTestUtils.setField(shop, "id", UUID.randomUUID());
        product = new Product(shop, new Category(null, "Veg", "veg", 0), "Carrot", null, ProductUnit.KG, 10_000, "KGS");
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        lenient().when(products.findById(product.getId())).thenReturn(Optional.of(product));
        lenient().when(shops.findById(shop.getId())).thenReturn(Optional.of(shop));
        lenient().when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void customerWhoDidNotReceiveProduct_cannotReview() {
        when(reviews.findByProductIdAndAuthorUserId(product.getId(), buyer)).thenReturn(Optional.empty());
        when(purchases.hasReceived(buyer, product.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.upsert(product.getId(), buyer, 5, "great"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(CatalogErrors.REVIEW_NOT_ALLOWED);
        verify(reviews, never()).save(any());
    }

    @Test
    void receivedProduct_reviewIsSavedAndRatingsRecalculated() {
        when(reviews.findByProductIdAndAuthorUserId(product.getId(), buyer)).thenReturn(Optional.empty());
        when(purchases.hasReceived(buyer, product.getId())).thenReturn(true);
        when(reviews.aggregateForProduct(product.getId())).thenReturn(List.<Object[]>of(new Object[]{4.25, 4L}));
        when(reviews.aggregateForShop(shop.getId())).thenReturn(List.<Object[]>of(new Object[]{4.0, 10L}));

        ProductReview review = service.upsert(product.getId(), buyer, 5, "  свежая морковь  ");

        assertThat(review.getText()).isEqualTo("свежая морковь");
        assertThat(product.getRating()).isEqualByComparingTo(new BigDecimal("4.3"));
        assertThat(product.getReviewsCount()).isEqualTo(4);
        assertThat(shop.getRating()).isEqualByComparingTo(new BigDecimal("4.0"));
        assertThat(shop.getReviewsCount()).isEqualTo(10);
    }

    @Test
    void secondSubmission_editsExistingReviewWithoutNewPurchaseCheck() {
        ProductReview existing = new ProductReview(product.getId(), shop.getId(), buyer, 2, "meh", java.time.Instant.now());
        when(reviews.findByProductIdAndAuthorUserId(product.getId(), buyer)).thenReturn(Optional.of(existing));
        when(reviews.aggregateForProduct(any())).thenReturn(List.<Object[]>of(new Object[]{4.0, 1L}));
        when(reviews.aggregateForShop(any())).thenReturn(List.<Object[]>of(new Object[]{4.0, 1L}));

        service.upsert(product.getId(), buyer, 4, "лучше, чем казалось");

        assertThat(existing.getRating()).isEqualTo(4);
        verify(purchases, never()).hasReceived(any(), any());
        verify(reviews, never()).save(any());
    }

    @Test
    void onlyShopOwnerCanReply() {
        ProductReview review = new ProductReview(product.getId(), shop.getId(), buyer, 3, "ok", java.time.Instant.now());
        ReflectionTestUtils.setField(review, "id", UUID.randomUUID());
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.reply(review.getId(), UUID.randomUUID(), "спасибо"))
                .isInstanceOf(DomainException.class);
        service.reply(review.getId(), owner, "Спасибо за отзыв!");

        assertThat(review.getReplyText()).isEqualTo("Спасибо за отзыв!");
    }

    @Test
    void hiddenReview_isExcludedFromRating() {
        ProductReview review = new ProductReview(product.getId(), shop.getId(), buyer, 1, "spam", java.time.Instant.now());
        ReflectionTestUtils.setField(review, "id", UUID.randomUUID());
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviews.aggregateForProduct(product.getId())).thenReturn(List.<Object[]>of(new Object[]{null, 0L}));
        when(reviews.aggregateForShop(shop.getId())).thenReturn(List.<Object[]>of(new Object[]{null, 0L}));

        service.setHidden(review.getId(), true);

        assertThat(review.isHidden()).isTrue();
        assertThat(product.getRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(product.getReviewsCount()).isZero();
    }

    @Test
    void summary_includesEveryStarLevel() {
        product.applyRating(new BigDecimal("4.5"), 2);
        when(reviews.distributionForProduct(product.getId()))
                .thenReturn(List.<Object[]>of(new Object[]{(short) 5, 1L}, new Object[]{(short) 4, 1L}));

        ReviewService.Summary summary = service.summary(product.getId());

        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.distribution()).containsEntry(5, 1L).containsEntry(4, 1L).containsEntry(1, 0L).hasSize(5);
    }
}
