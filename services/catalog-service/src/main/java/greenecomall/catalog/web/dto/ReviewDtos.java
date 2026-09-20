package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.ProductReview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String text) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 2000) String text) {
    }

    /**
     * Публичный вид: автор не раскрывается (только признак «покупатель»); {@code mine} — отзыв текущего
     * пользователя, чтобы фронт показал «изменить/удалить».
     */
    public record ReviewResponse(UUID id, UUID productId, int rating, String text, boolean verifiedPurchase,
                                 String shopReply, Instant repliedAt, Instant createdAt, Instant updatedAt,
                                 boolean mine) {
        public static ReviewResponse from(ProductReview r, UUID viewerUserId) {
            return new ReviewResponse(r.getId(), r.getProductId(), r.getRating(), r.getText(), true,
                    r.getReplyText(), r.getRepliedAt(), r.getCreatedAt(), r.getUpdatedAt(),
                    viewerUserId != null && viewerUserId.equals(r.getAuthorUserId()));
        }
    }

    /** Для кабинета магазина и админки: плюс статус модерации. */
    public record ManagedReviewResponse(UUID id, UUID productId, UUID shopId, int rating, String text,
                                        String shopReply, boolean hidden, Instant createdAt) {
        public static ManagedReviewResponse from(ProductReview r) {
            return new ManagedReviewResponse(r.getId(), r.getProductId(), r.getShopId(), r.getRating(), r.getText(),
                    r.getReplyText(), r.isHidden(), r.getCreatedAt());
        }
    }

    public record SummaryResponse(BigDecimal average, int count, Map<Integer, Long> distribution) {
    }
}
