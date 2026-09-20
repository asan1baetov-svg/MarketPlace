package greenecomall.order.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.PageResponse;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderStatus;
import greenecomall.order.order.OrderService;
import greenecomall.order.order.ShopSuborderService;
import greenecomall.order.web.dto.ShopSuborderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Эндпоинты магазина по своим suborders. У пользователя может быть несколько магазинов, поэтому
 * {@code shopId} передаётся явно, а владение проверяется {@link ShopAccess} по {@code sub} токена.
 * Ответ намеренно не содержит наценку/цену продажи (см. {@link ShopSuborderResponse}).
 */
@RestController
public class ShopSuborderController {

    private final ShopSuborderService shopSuborders;
    private final OrderService orderService;
    private final ShopAccess shopAccess;

    public ShopSuborderController(ShopSuborderService shopSuborders, OrderService orderService, ShopAccess shopAccess) {
        this.shopSuborders = shopSuborders;
        this.orderService = orderService;
        this.shopAccess = shopAccess;
    }

    @GetMapping("/shop/suborders")
    public PageResponse<ShopSuborderResponse> list(
            @RequestParam UUID shopId,
            @RequestParam(required = false) SuborderStatus status,
            Pageable pageable,
            AuthPrincipal principal) {
        Page<Suborder> page = shopSuborders.list(shopAccess.requireOwnShop(principal, shopId), status, pageable);
        List<ShopSuborderResponse> content = page.getContent().stream()
                .map(s -> ShopSuborderResponse.of(s, currencyOf(s), orderService.suborderItems(s.getId())))
                .toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @PostMapping("/shop/suborders/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID id, @RequestParam UUID shopId, AuthPrincipal principal) {
        shopSuborders.accept(id, shopAccess.requireOwnShop(principal, shopId));
    }

    @PostMapping("/shop/suborders/{id}/assemble")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assemble(@PathVariable UUID id, @RequestParam UUID shopId, AuthPrincipal principal) {
        shopSuborders.assemble(id, shopAccess.requireOwnShop(principal, shopId));
    }

    private String currencyOf(Suborder suborder) {
        return orderService.get(suborder.getOrderId()).getCurrency();
    }
}
