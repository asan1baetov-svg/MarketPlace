package greenecomall.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Фото товара. {@link Kind#ORIGINAL} — то, что загрузил магазин (или внешняя ссылка);
 * {@link Kind#AI} — вариант, который ИИ делает из оригинала в выбранном {@link PhotoStyle} (с учётом
 * пожелания магазина): сам товар не меняется, меняются фон, свет, окружение, люди в кадре.
 * Витрина показывает только {@code published && READY}.
 */
@Entity
@Table(name = "product_images")
public class ProductImage {

    public enum Kind {ORIGINAL, AI}

    public enum Status {PENDING, PROCESSING, READY, FAILED}

    /** Сколько раз пробуем сгенерировать вариант, прежде чем сдаться (FAILED). */
    public static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(length = 500)
    private String url;

    @Column(nullable = false)
    private int sort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Kind kind = Kind.ORIGINAL;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, updatable = false)
    private PhotoStyle style;

    @Column(length = 200, updatable = false)
    private String wish;

    @Column(name = "source_image_id", updatable = false)
    private UUID sourceImageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.READY;

    @Column(nullable = false)
    private boolean published = true;

    @Column(name = "storage_key", length = 300)
    private String storageKey;

    @Column(length = 300)
    private String error;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ProductImage() {
    }

    /** Фото по внешней ссылке — публикуется сразу, ИИ его не обрабатывает. */
    public ProductImage(Product product, String url, int sort) {
        this.product = product;
        this.url = url;
        this.sort = sort;
    }

    /** Оригинал, загруженный магазином в хранилище; виден сразу, пока ИИ готовит варианты. */
    public static ProductImage uploaded(Product product, String storageKey, String url, int sort) {
        ProductImage image = new ProductImage(product, url, sort);
        image.storageKey = storageKey;
        return image;
    }

    /** Вариант ИИ в выбранном стиле, ждущий генерации; не опубликован, пока не готов. */
    public static ProductImage pendingVariant(Product product, ProductImage source, PhotoStyle style, String wish,
                                              int sort) {
        ProductImage image = new ProductImage(product, null, sort);
        image.kind = Kind.AI;
        image.style = style;
        image.wish = wish;
        image.sourceImageId = source.getId();
        image.status = Status.PENDING;
        image.published = false;
        return image;
    }

    public void startProcessing() {
        this.status = Status.PROCESSING;
        this.attempts++;
    }

    public void markReady(String storageKey, String url) {
        this.storageKey = storageKey;
        this.url = url;
        this.status = Status.READY;
        this.error = null;
        this.published = true;
    }

    /** Сбой генерации: повторим позже, после {@link #MAX_ATTEMPTS} — FAILED (оригинал остаётся на витрине). */
    public void markFailed(String error) {
        this.error = error == null || error.length() <= 300 ? error : error.substring(0, 300);
        this.status = attempts >= MAX_ATTEMPTS ? Status.FAILED : Status.PENDING;
    }

    /** Магазину не понравился результат — сгенерировать заново. */
    public void regenerate() {
        this.status = Status.PENDING;
        this.attempts = 0;
        this.error = null;
        this.published = false;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public boolean isVisible() {
        return published && status == Status.READY && url != null;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getUrl() {
        return url;
    }

    public int getSort() {
        return sort;
    }

    public Kind getKind() {
        return kind;
    }

    public PhotoStyle getStyle() {
        return style;
    }

    public String getWish() {
        return wish;
    }

    public UUID getSourceImageId() {
        return sourceImageId;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPublished() {
        return published;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getError() {
        return error;
    }

    public int getAttempts() {
        return attempts;
    }
}
