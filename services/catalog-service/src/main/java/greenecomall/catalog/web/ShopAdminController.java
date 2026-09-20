package greenecomall.catalog.web;

import greenecomall.catalog.domain.ShopStatus;
import greenecomall.catalog.shop.ShopService;
import greenecomall.catalog.web.dto.ReasonRequest;
import greenecomall.catalog.web.dto.ShopResponse;
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
public class ShopAdminController {

    private final ShopService shopService;

    public ShopAdminController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/admin/shops")
    public Page<ShopResponse> list(@RequestParam ShopStatus status, Pageable pageable) {
        return shopService.listByStatus(status, pageable).map(ShopResponse::from);
    }

    @PostMapping("/admin/shops/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID id) {
        shopService.approve(id);
    }

    @PostMapping("/admin/shops/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        shopService.reject(id, request.reason());
    }

    @PostMapping("/admin/shops/{id}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        shopService.suspend(id, request.reason());
    }
}
