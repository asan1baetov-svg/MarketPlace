package greenecomall.catalog.web;

import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductImage;
import greenecomall.catalog.pricing.MarkupService;
import greenecomall.catalog.product.ProductService;
import greenecomall.catalog.repo.ProductImageRepository;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.catalog.web.dto.StorefrontProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Гео-фильтрованная витрина для клиента/гостя. Ценовой (min/max) и рейтинговый фильтры, а также
 * Redis-кэш резолва цены — из MVP сознательно исключены (см. {@code ProductRepository.searchStorefront}
 * и {@code MarkupService}), это следующий шаг оптимизации/фич поверх уже корректной цены.
 */
@RestController
@RequestMapping("/catalog/products")
public class StorefrontController {

    private final ProductRepository products;
    private final ProductService productService;
    private final MarkupService markupService;
    private final ProductImageRepository productImages;

    public StorefrontController(ProductRepository products, ProductService productService,
                                MarkupService markupService, ProductImageRepository productImages) {
        this.products = products;
        this.productService = productService;
        this.markupService = markupService;
        this.productImages = productImages;
    }

    @GetMapping
    public Page<StorefrontProductResponse> search(
            @RequestParam UUID cityId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID shopId,
            Pageable pageable) {
        Page<Product> page = products.searchStorefront(cityId, categoryId, shopId, pageable);
        Map<UUID, List<String>> images = images(page.getContent().stream().map(Product::getId).toList());
        return page.map(product -> StorefrontProductResponse.from(product, markupService.resolve(product, cityId),
                images.getOrDefault(product.getId(), List.of())));
    }

    @GetMapping("/{id}")
    public StorefrontProductResponse get(@PathVariable UUID id, @RequestParam(required = false) UUID cityId) {
        Product product = productService.requireSellable(id, cityId);
        UUID effectiveCityId = cityId != null ? cityId : product.getCity().getId();
        return StorefrontProductResponse.from(product, markupService.resolve(product, effectiveCityId),
                images(List.of(id)).getOrDefault(id, List.of()));
    }

    private Map<UUID, List<String>> images(List<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productImages.findVisibleForProducts(productIds).stream().collect(Collectors.groupingBy(
                image -> image.getProduct().getId(), LinkedHashMap::new,
                Collectors.mapping(ProductImage::getUrl, Collectors.toList())));
    }
}
