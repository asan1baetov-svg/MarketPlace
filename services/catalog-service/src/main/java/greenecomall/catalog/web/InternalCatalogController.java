package greenecomall.catalog.web;

import greenecomall.catalog.pricing.MarkupService;
import greenecomall.catalog.product.ProductService;
import greenecomall.catalog.shop.ShopService;
import greenecomall.catalog.stock.StockService;
import greenecomall.catalog.web.dto.InternalShopResponse;
import greenecomall.catalog.web.dto.PriceResponse;
import greenecomall.catalog.web.dto.StockOpRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Служебное API для order-service: резолв цены при checkout, резерв/релиз/коммит остатков,
 * владелец магазина. Доступ — только с {@code X-Internal-Token} (см. {@code ServiceSecurityConfiguration}).
 */
@RestController
@RequestMapping("/internal/catalog")
public class InternalCatalogController {

    private final ProductService productService;
    private final MarkupService markupService;
    private final StockService stockService;
    private final ShopService shopService;

    public InternalCatalogController(ProductService productService, MarkupService markupService,
                                     StockService stockService, ShopService shopService) {
        this.productService = productService;
        this.markupService = markupService;
        this.stockService = stockService;
        this.shopService = shopService;
    }

    @GetMapping("/shops/{id}")
    public InternalShopResponse shop(@PathVariable UUID id) {
        return InternalShopResponse.from(shopService.get(id));
    }

    @GetMapping("/price")
    public PriceResponse price(@RequestParam UUID productId, @RequestParam UUID cityId) {
        // checkout: товар могли снять с продажи/архивировать после добавления в корзину
        var product = productService.requireSellable(productId, cityId);
        return PriceResponse.from(markupService.resolve(product, cityId));
    }

    @PostMapping("/stock/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@Valid @RequestBody StockOpRequest request) {
        stockService.reserve(request.productId(), request.qty());
    }

    @PostMapping("/stock/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@Valid @RequestBody StockOpRequest request) {
        stockService.release(request.productId(), request.qty());
    }

    @PostMapping("/stock/commit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void commit(@Valid @RequestBody StockOpRequest request) {
        stockService.commit(request.productId(), request.qty());
    }
}
