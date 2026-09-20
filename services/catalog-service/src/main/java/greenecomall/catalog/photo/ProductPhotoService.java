package greenecomall.catalog.photo;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.PhotoStyle;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductImage;
import greenecomall.catalog.repo.ProductImageRepository;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.common.domain.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Фото товара: магазин загружает оригинал, ИИ делает из него студийную обложку и кадры в стилях,
 * которые магазин выбрал ({@link PhotoStyle}: в интерьере, с человеком, в руках…), с учётом
 * названия/описания товара и пожелания магазина. Оригинал виден сразу; готовая обложка его заменяет.
 * Магазин может снять с публикации любое фото, заказать новый стиль или сгенерировать заново.
 */
@Service
public class ProductPhotoService {

    /** Сколько ИИ-кадров можно заказать на один товар — каждый кадр стоит денег. */
    static final int MAX_AI_PER_PRODUCT = 30;

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ImageStorage storage;
    private final PhotoProperties properties;

    public ProductPhotoService(ProductRepository products, ProductImageRepository images, ImageStorage storage,
                               PhotoProperties properties) {
        this.products = products;
        this.images = images;
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * Загрузка оригинала. Студийная обложка делается всегда, плюс кадры в {@code styles}, выбранных
     * магазином; {@code wish} — пожелание ко всем кадрам. {@code callerUserId == null} — админ.
     */
    @Transactional
    public List<ProductImage> upload(UUID productId, UUID callerUserId, byte[] data, List<PhotoStyle> styles,
                                     String wish) {
        Product product = requireOwnProduct(productId, callerUserId);
        if (data == null || data.length == 0 || data.length > properties.maxUploadMb() * 1024L * 1024L) {
            throw new DomainException(CatalogErrors.IMAGE_INVALID, "photo must be 1 byte .. " + properties.maxUploadMb() + " MB");
        }
        ImageType type = ImageType.detect(data)
                .orElseThrow(() -> new DomainException(CatalogErrors.IMAGE_INVALID, "only JPEG, PNG or WebP photos are accepted"));
        String key = key(productId, type.extension());
        String url = storage.put(key, data, type.mimeType());

        Set<PhotoStyle> wanted = new LinkedHashSet<>();
        wanted.add(PhotoStyle.STUDIO);
        if (styles != null) {
            wanted.addAll(styles);
        }
        int base = images.maxSort(productId) + 10;
        ProductImage original = images.save(ProductImage.uploaded(product, key, url, base + wanted.size()));
        if (!properties.ai().enabled()) {
            return List.of(original);
        }
        requireAiQuota(productId, wanted.size());
        List<ProductImage> result = new ArrayList<>();
        int sort = base;
        for (PhotoStyle style : wanted) {
            result.add(images.save(ProductImage.pendingVariant(product, original, style, cleanWish(wish), sort++)));
        }
        result.add(original);
        return result;
    }

    /** Заказать ещё один кадр в выбранном стиле из уже загруженного оригинала. */
    @Transactional
    public ProductImage generate(UUID productId, UUID sourceImageId, UUID callerUserId, PhotoStyle style, String wish) {
        Product product = requireOwnProduct(productId, callerUserId);
        ProductImage source = requireImage(productId, sourceImageId);
        if (source.getKind() != ProductImage.Kind.ORIGINAL || source.getStorageKey() == null) {
            throw new DomainException(CatalogErrors.IMAGE_INVALID, "choose an uploaded original photo");
        }
        if (!properties.ai().enabled()) {
            throw new DomainException(CatalogErrors.IMAGE_INVALID, "AI photos are turned off");
        }
        requireAiQuota(productId, 1);
        return images.save(ProductImage.pendingVariant(product, source, style, cleanWish(wish),
                images.maxSort(productId) + 10));
    }

    /** Стили для выбора в кабинете магазина. */
    public List<PhotoStyle> styles() {
        return List.of(PhotoStyle.values());
    }

    @Transactional
    public ProductImage setPublished(UUID productId, UUID imageId, UUID callerUserId, boolean published) {
        requireOwnProduct(productId, callerUserId);
        ProductImage image = requireImage(productId, imageId);
        if (published && image.getStatus() != ProductImage.Status.READY) {
            throw new DomainException(CatalogErrors.IMAGE_INVALID, "photo is not ready yet");
        }
        image.setPublished(published);
        return image;
    }

    /** Не понравился результат ИИ — сделать заново (промпт возьмёт актуальные название/описание). */
    @Transactional
    public ProductImage regenerate(UUID productId, UUID imageId, UUID callerUserId) {
        requireOwnProduct(productId, callerUserId);
        ProductImage image = requireImage(productId, imageId);
        if (image.getKind() != ProductImage.Kind.AI) {
            throw new DomainException(CatalogErrors.IMAGE_INVALID, "only AI photos can be regenerated");
        }
        image.regenerate();
        return image;
    }

    // ─── очередь генерации (AutoPhotoJob) ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UUID> pending(int limit) {
        return images.findIdsByStatus(ProductImage.Status.PENDING, PageRequest.of(0, limit));
    }

    /** Шаг 1 (под блокировкой строки): PENDING → PROCESSING, всё нужное для генерации. */
    @Transactional
    public Optional<Work> claim(UUID imageId) {
        ProductImage variant = images.findWithLockById(imageId).orElse(null);
        if (variant == null || variant.getStatus() != ProductImage.Status.PENDING) {
            return Optional.empty();
        }
        ProductImage source = images.findById(variant.getSourceImageId()).orElse(null);
        if (source == null || source.getStorageKey() == null) {
            variant.startProcessing();
            variant.markFailed("source photo is missing");
            return Optional.empty();
        }
        variant.startProcessing();
        Product product = variant.getProduct();
        return Optional.of(new Work(variant.getId(), product.getId(), variant.getStyle(), source.getStorageKey(),
                PhotoPromptBuilder.build(variant.getStyle(), product, variant.getWish())));
    }

    public byte[] read(String key) {
        return storage.get(key);
    }

    /** Шаг 2, успех: сохранить результат; готовая обложка заменяет оригинал на витрине. */
    @Transactional
    public void complete(UUID imageId, ProductPhotoAi.Rendered rendered) {
        ProductImage variant = images.findById(imageId).orElseThrow();
        ImageType type = ImageType.detect(rendered.data()).orElse(ImageType.PNG);
        String key = key(variant.getProduct().getId(), type.extension());
        variant.markReady(key, storage.put(key, rendered.data(), type.mimeType()));
        if (variant.getStyle() == PhotoStyle.STUDIO) {
            images.findById(variant.getSourceImageId()).ifPresent(source -> source.setPublished(false));
        }
    }

    @Transactional
    public void fail(UUID imageId, String error) {
        images.findById(imageId).ifPresent(image -> image.markFailed(error));
    }

    private Product requireOwnProduct(UUID productId, UUID callerUserId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new DomainException(CatalogErrors.PRODUCT_NOT_FOUND, "product not found: " + productId));
        if (callerUserId != null && !product.getShop().isOwnedBy(callerUserId)) {
            throw new DomainException(CatalogErrors.PRODUCT_FORBIDDEN, "caller does not own this shop");
        }
        return product;
    }

    private ProductImage requireImage(UUID productId, UUID imageId) {
        ProductImage image = images.findById(imageId)
                .orElseThrow(() -> new DomainException(CatalogErrors.IMAGE_NOT_FOUND, "photo not found: " + imageId));
        if (!image.getProduct().getId().equals(productId)) {
            throw new DomainException(CatalogErrors.IMAGE_NOT_FOUND, "photo not found: " + imageId);
        }
        return image;
    }

    private void requireAiQuota(UUID productId, int adding) {
        if (images.countByProductIdAndKind(productId, ProductImage.Kind.AI) + adding > MAX_AI_PER_PRODUCT) {
            throw new DomainException(CatalogErrors.IMAGE_INVALID,
                    "AI photo limit reached (" + MAX_AI_PER_PRODUCT + " per product) — delete unused ones first");
        }
    }

    private static String cleanWish(String wish) {
        if (wish == null || wish.isBlank()) {
            return null;
        }
        String flat = wish.replaceAll("\\s+", " ").strip();
        return flat.length() <= 200 ? flat : flat.substring(0, 200);
    }

    private static String key(UUID productId, String extension) {
        return "products/" + productId + "/" + UUID.randomUUID() + "." + extension;
    }

    public record Work(UUID imageId, UUID productId, PhotoStyle style, String sourceKey, String prompt) {
    }
}
