package greenecomall.order.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.security.Authz;
import greenecomall.order.cart.CartService;
import greenecomall.order.web.dto.AddCartItemRequest;
import greenecomall.order.web.dto.CartResponse;
import greenecomall.order.web.dto.SetQtyRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Мультивендорная корзина клиента. Владелец корзины — {@code sub} проверенного access-JWT. */
@RestController
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/cart")
    public CartResponse view(AuthPrincipal principal) {
        return CartResponse.from(cartService.view(Authz.require(principal).userId()));
    }

    @PostMapping("/cart/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request, AuthPrincipal principal) {
        return CartResponse.from(cartService.addItem(
                Authz.require(principal).userId(), request.productId(), request.cityId(), request.qty()));
    }

    @PutMapping("/cart/items/{itemId}")
    public CartResponse setQty(@PathVariable UUID itemId, @Valid @RequestBody SetQtyRequest request,
                               AuthPrincipal principal) {
        return CartResponse.from(cartService.setQty(Authz.require(principal).userId(), itemId, request.qty()));
    }

    @DeleteMapping("/cart/items/{itemId}")
    public CartResponse removeItem(@PathVariable UUID itemId, AuthPrincipal principal) {
        return CartResponse.from(cartService.removeItem(Authz.require(principal).userId(), itemId));
    }
}
