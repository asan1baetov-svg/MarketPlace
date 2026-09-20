package greenecomall.catalog.product;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.Category;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductImage;
import greenecomall.catalog.domain.ProductStatus;
import greenecomall.catalog.domain.ProductStock;
import greenecomall.catalog.domain.ProductUnit;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.domain.ShopStatus;
import greenecomall.catalog.pricing.MarkupService;
import greenecomall.catalog.pricing.PriceResolution;
import greenecomall.catalog.repo.CategoryRepository;
import greenecomall.catalog.repo.ProductImageRepository;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.catalog.repo.ProductStockRepository;
import greenecomall.catalog.shop.ShopService;
import greenecomall.catalog.support.Tracing;
import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.CatalogEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private static final String PRODUCER = "catalog-service";

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductStockRepository stocks;
    private final CategoryRepository categories;
    private final ShopService shopService;
    private final MarkupService markupService;
    private final DomainEventPublisher events;

    public ProductService(ProductRepository products, ProductImageRepository images, ProductStockRepository stocks,
                          CategoryRepository categories, ShopService shopService, MarkupService markupService,
                          DomainEventPublisher events) {
        this.products = products;
        this.images = images;
        this.stocks = stocks;
        this.categories = categories;
        this.shopService = shopService;
        this.markupService = markupService;
        this.events = events;
    }

    @Transactional
    public UUID create(UUID shopId, UUID callerUserId, UUID categoryId, String name, String description,
                       ProductUnit unit, long costPriceMinor, String currency) {
        Shop shop = shopService.requireShop(shopId);
        requireOwnerOrThrow(shop, callerUserId);
        Category category = requireCategory(categoryId);
        Product product = new Product(shop, category, name, description, unit, costPriceMinor, currency);
        products.save(product);
        stocks.save(new ProductStock(product, 0));
        return product.getId();
    }

    @Transactional(readOnly = true)
    public Product get(UUID id) {
        return requireProduct(id);
    }

    /**
     * Товар, который сейчас можно купить: опубликован, магазин активен и (если город задан) товар
     * продаётся в этом городе. Иначе — 404, как будто товара нет: витрина и checkout не должны
     * видеть черновики, архив и товары приостановленных магазинов.
     */
    @Transactional(readOnly = true)
    public Product requireSellable(UUID id, UUID cityId) {
        Product product = requireProduct(id);
        boolean sellable = product.getStatus() == ProductStatus.PUBLISHED
                && product.getShop().getStatus() == ShopStatus.ACTIVE
                && (cityId == null || product.getCity().getId().equals(cityId));
        if (!sellable) {
            throw new DomainException(CatalogErrors.PRODUCT_NOT_FOUND, "product is not on sale: " + id);
        }
        return product;
    }

    @Transactional
    public void update(UUID id, UUID callerUserId, UUID categoryId, String name, String description,
                       ProductUnit unit, long costPriceMinor, String currency) {
        Product product = requireProduct(id);
        requireOwnerOrThrow(product.getShop(), callerUserId);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new DomainException(CatalogErrors.PRODUCT_STATUS_INVALID, "cannot edit an archived product");
        }
        Category category = requireCategory(categoryId);
        product.update(category, name, description, unit, costPriceMinor, currency);
    }

    @Transactional
    public void submitForModeration(UUID id, UUID callerUserId) {
        Product product = requireProduct(id);
        requireOwnerOrThrow(product.getShop(), callerUserId);
        if (product.getStatus() != ProductStatus.DRAFT && product.getStatus() != ProductStatus.REJECTED) {
            throw new DomainException(CatalogErrors.PRODUCT_STATUS_INVALID, "only a draft/rejected product can be submitted");
        }
        product.submitForModeration();
    }

    @Transactional
    public void publish(UUID id) {
        Product product = requireProduct(id);
        if (product.getStatus() != ProductStatus.MODERATION) {
            throw new DomainException(CatalogErrors.PRODUCT_STATUS_INVALID, "product is not pending moderation");
        }
        product.publish();
        PriceResolution price = markupService.resolve(product, product.getCity().getId());
        events.publish(Topics.CATALOG, product.getId().toString(),
                EventEnvelope.of(EventTypes.PRODUCT_PUBLISHED, PRODUCER, Tracing.currentTraceId(),
                        new CatalogEvents.ProductPublished(
                                product.getId(), product.getShop().getId(), product.getCity().getId(),
                                product.getCategory().getId(), price.salePriceMinor(), price.currency())));
    }

    @Transactional
    public void reject(UUID id, String reason) {
        Product product = requireProduct(id);
        if (product.getStatus() != ProductStatus.MODERATION) {
            throw new DomainException(CatalogErrors.PRODUCT_STATUS_INVALID, "product is not pending moderation");
        }
        product.reject(reason);
        events.publish(Topics.CATALOG, product.getId().toString(),
                EventEnvelope.of(EventTypes.PRODUCT_REJECTED, PRODUCER, Tracing.currentTraceId(),
                        new CatalogEvents.ProductRejected(product.getId(), product.getShop().getId(), reason)));
    }

    @Transactional
    public void archive(UUID id, UUID callerUserId) {
        Product product = requireProduct(id);
        requireOwnerOrThrow(product.getShop(), callerUserId);
        product.archive();
    }

    @Transactional
    public UUID addImage(UUID productId, UUID callerUserId, String url) {
        Product product = requireProduct(productId);
        requireOwnerOrThrow(product.getShop(), callerUserId);
        return images.save(new ProductImage(product, url, images.maxSort(productId) + 10)).getId();
    }

    @Transactional(readOnly = true)
    public List<ProductImage> listImages(UUID productId) {
        return images.findByProductIdOrderBySortAsc(productId);
    }

    @Transactional
    public void removeImage(UUID productId, UUID imageId, UUID callerUserId) {
        Product product = requireProduct(productId);
        requireOwnerOrThrow(product.getShop(), callerUserId);
        images.deleteById(imageId);
    }

    @Transactional
    public void setStock(UUID productId, UUID callerUserId, int quantity) {
        Product product = requireProduct(productId);
        requireOwnerOrThrow(product.getShop(), callerUserId);
        ProductStock stock = stocks.findByProductId(productId)
                .orElseThrow(() -> new DomainException(CatalogErrors.STOCK_NOT_FOUND, "stock row missing for product " + productId));
        if (quantity < stock.getReservedQuantity()) {
            // иначе резервов больше, чем товара, и оплаченные заказы нечем выполнить
            throw new DomainException(CatalogErrors.STOCK_INSUFFICIENT, "quantity " + quantity
                    + " is below reserved " + stock.getReservedQuantity() + " for product " + productId);
        }
        stock.setQuantity(quantity);
        events.publish(Topics.CATALOG, productId.toString(),
                EventEnvelope.of(EventTypes.STOCK_CHANGED, PRODUCER, Tracing.currentTraceId(),
                        new CatalogEvents.StockChanged(productId, stock.getQuantity(), stock.getReservedQuantity())));
    }

    private void requireOwnerOrThrow(Shop shop, UUID callerUserId) {
        if (callerUserId != null && !shop.isOwnedBy(callerUserId)) {
            throw new DomainException(CatalogErrors.PRODUCT_FORBIDDEN, "caller does not own this shop");
        }
        if (shop.getStatus() != ShopStatus.ACTIVE && shop.getStatus() != ShopStatus.MODERATION
                && shop.getStatus() != ShopStatus.DRAFT) {
            throw new DomainException(CatalogErrors.SHOP_NOT_ACTIVE, "shop is not in a state that allows product changes");
        }
    }

    private Product requireProduct(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.PRODUCT_NOT_FOUND, "product not found: " + id));
    }

    private Category requireCategory(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.CATEGORY_NOT_FOUND, "category not found: " + id));
    }
}
