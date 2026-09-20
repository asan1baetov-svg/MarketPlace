package greenecomall.catalog.web;

import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductStatus;
import greenecomall.catalog.product.ProductService;
import greenecomall.catalog.web.dto.ProductResponse;
import greenecomall.catalog.web.dto.ReasonRequest;
import greenecomall.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ProductAdminController {

    private final ProductService productService;

    public ProductAdminController(ProductService productService) {
        this.productService = productService;
    }

    /** Очередь модерации и поиск по товарам: {@code status=MODERATION} — то, что ждёт решения. */
    @GetMapping("/admin/products")
    public PageResponse<ProductResponse> search(@RequestParam(required = false) ProductStatus status,
                                                @RequestParam(required = false) UUID shopId,
                                                @RequestParam(required = false) UUID cityId,
                                                Pageable pageable) {
        Page<Product> page = productService.adminSearch(status, shopId, cityId, pageable);
        return PageResponse.of(page.getContent().stream().map(ProductResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @PostMapping("/admin/products/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(@PathVariable UUID id) {
        productService.publish(id);
    }

    @PostMapping("/admin/products/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        productService.reject(id, request.reason());
    }
}
