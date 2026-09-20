package greenecomall.catalog.web;

import greenecomall.catalog.review.ReviewService;
import greenecomall.catalog.shop.ShopService;
import greenecomall.catalog.web.dto.ReviewDtos.ManagedReviewResponse;
import greenecomall.catalog.web.dto.ReviewDtos.ReplyRequest;
import greenecomall.catalog.web.dto.ReviewDtos.ReviewRequest;
import greenecomall.catalog.web.dto.ReviewDtos.ReviewResponse;
import greenecomall.catalog.web.dto.ReviewDtos.SummaryResponse;
import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.PageResponse;
import greenecomall.common.web.security.Authz;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Function;

/**
 * Отзывы: читать может любой (витрина), писать — покупатель, получивший товар, отвечать — владелец
 * магазина, скрывать — админ.
 */
@RestController
public class ReviewController {

    private final ReviewService reviews;
    private final ShopService shops;

    public ReviewController(ReviewService reviews, ShopService shops) {
        this.reviews = reviews;
        this.shops = shops;
    }

    @GetMapping("/catalog/products/{productId}/reviews")
    public PageResponse<ReviewResponse> list(@PathVariable UUID productId, Pageable pageable, AuthPrincipal principal) {
        UUID viewer = principal == null ? null : principal.userId();
        return page(reviews.visibleForProduct(productId, pageable), r -> ReviewResponse.from(r, viewer));
    }

    @GetMapping("/catalog/products/{productId}/reviews/summary")
    public SummaryResponse summary(@PathVariable UUID productId) {
        ReviewService.Summary s = reviews.summary(productId);
        return new SummaryResponse(s.average(), s.count(), s.distribution());
    }

    /** Оставить или исправить свой отзыв (один на товар). */
    @PutMapping("/products/{productId}/reviews/mine")
    public ReviewResponse upsert(@PathVariable UUID productId, @Valid @RequestBody ReviewRequest request,
                                 AuthPrincipal principal) {
        UUID userId = Authz.require(principal).userId();
        return ReviewResponse.from(reviews.upsert(productId, userId, request.rating(), request.text()), userId);
    }

    @DeleteMapping("/products/{productId}/reviews/mine")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(@PathVariable UUID productId, AuthPrincipal principal) {
        reviews.deleteOwn(productId, Authz.require(principal).userId());
    }

    /** Отзывы о товарах магазина — для кабинета (включая скрытые, чтобы видеть модерацию). */
    @GetMapping("/shops/{shopId}/reviews")
    public PageResponse<ManagedReviewResponse> forShop(@PathVariable UUID shopId, Pageable pageable,
                                                       AuthPrincipal principal) {
        Authz.requireSelfOrAdmin(principal, shops.get(shopId).getOwnerUserId());
        return page(reviews.forShop(shopId, pageable), ManagedReviewResponse::from);
    }

    @PostMapping("/reviews/{reviewId}/reply")
    public ManagedReviewResponse reply(@PathVariable UUID reviewId, @Valid @RequestBody ReplyRequest request,
                                       AuthPrincipal principal) {
        Authz.require(principal);
        UUID owner = Authz.isAdmin(principal) ? null : principal.userId();
        return ManagedReviewResponse.from(reviews.reply(reviewId, owner, request.text()));
    }

    // ─── админ ────────────────────────────────────────────────────────────

    @GetMapping("/admin/reviews")
    public PageResponse<ManagedReviewResponse> adminList(@RequestParam(defaultValue = "false") boolean hidden,
                                                         Pageable pageable) {
        return page(reviews.byHidden(hidden, pageable), ManagedReviewResponse::from);
    }

    @PostMapping("/admin/reviews/{reviewId}/hide")
    public ManagedReviewResponse hide(@PathVariable UUID reviewId) {
        return ManagedReviewResponse.from(reviews.setHidden(reviewId, true));
    }

    @PostMapping("/admin/reviews/{reviewId}/unhide")
    public ManagedReviewResponse unhide(@PathVariable UUID reviewId) {
        return ManagedReviewResponse.from(reviews.setHidden(reviewId, false));
    }

    private static <T, R> PageResponse<R> page(Page<T> page, Function<T, R> mapper) {
        return PageResponse.of(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
