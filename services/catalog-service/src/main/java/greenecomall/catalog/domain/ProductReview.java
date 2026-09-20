package greenecomall.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Отзыв покупателя о товаре: оценка 1–5, текст, ответ магазина. Скрытый админом не учитывается в рейтинге. */
@Entity
@Table(name = "product_reviews")
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "shop_id", nullable = false, updatable = false)
    private UUID shopId;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;

    @Column(nullable = false)
    private short rating;

    @Column(length = 2000)
    private String text;

    @Column(name = "reply_text", length = 2000)
    private String replyText;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductReview() {
    }

    public ProductReview(UUID productId, UUID shopId, UUID authorUserId, int rating, String text, Instant now) {
        this.productId = productId;
        this.shopId = shopId;
        this.authorUserId = authorUserId;
        this.rating = (short) rating;
        this.text = text;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(int rating, String text, Instant now) {
        this.rating = (short) rating;
        this.text = text;
        this.updatedAt = now;
    }

    public void reply(String text, Instant now) {
        this.replyText = text;
        this.repliedAt = now;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getShopId() {
        return shopId;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public int getRating() {
        return rating;
    }

    public String getText() {
        return text;
    }

    public String getReplyText() {
        return replyText;
    }

    public Instant getRepliedAt() {
        return repliedAt;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
