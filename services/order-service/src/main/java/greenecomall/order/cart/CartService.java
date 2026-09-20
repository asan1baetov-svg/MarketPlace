package greenecomall.order.cart;

import greenecomall.common.domain.DomainException;
import greenecomall.order.OrderErrors;
import greenecomall.order.catalog.CatalogClient;
import greenecomall.order.domain.Cart;
import greenecomall.order.domain.CartItem;
import greenecomall.order.repo.CartItemRepository;
import greenecomall.order.repo.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Мультивендорная корзина: товары из разных магазинов, но <b>одного города</b>
 * (см. docs/ARCHITECTURE.md §2.3). Город фиксируется первым добавленным товаром;
 * сменить его можно только на пустой корзине.
 */
@Service
public class CartService {

    private final CartRepository carts;
    private final CartItemRepository items;
    private final CatalogClient catalog;

    public CartService(CartRepository carts, CartItemRepository items, CatalogClient catalog) {
        this.carts = carts;
        this.items = items;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public CartView view(UUID clientUserId) {
        Cart cart = carts.findByClientUserId(clientUserId).orElse(null);
        if (cart == null) {
            return new CartView(null, null, List.of());
        }
        return new CartView(cart.getId(), cart.getCityId(), items.findByCartIdOrderByAddedAtAsc(cart.getId()));
    }

    @Transactional
    public CartView addItem(UUID clientUserId, UUID productId, UUID cityId, int qty) {
        Cart cart = carts.findByClientUserId(clientUserId).orElseGet(() -> carts.save(new Cart(clientUserId, cityId)));
        List<CartItem> existing = items.findByCartIdOrderByAddedAtAsc(cart.getId());
        if (!cart.getCityId().equals(cityId)) {
            if (!existing.isEmpty()) {
                throw new DomainException(OrderErrors.CART_CITY_MISMATCH,
                        "cart is bound to city " + cart.getCityId() + "; empty it before switching city");
            }
            cart.setCityId(cityId);
        }

        CatalogClient.StorefrontProduct product = catalog.storefrontProduct(productId, cityId);
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId).orElse(null);
        if (item == null) {
            item = new CartItem(cart.getId(), productId, product.shopId(), product.name(), qty,
                    product.salePriceMinor(), product.currency());
            items.save(item);
        } else {
            item.changeQty(item.getQty() + qty);
            item.refreshPrice(product.salePriceMinor(), product.currency());
        }
        return view(clientUserId);
    }

    @Transactional
    public CartView setQty(UUID clientUserId, UUID itemId, int qty) {
        Cart cart = requireCart(clientUserId);
        CartItem item = items.findById(itemId)
                .filter(i -> i.getCartId().equals(cart.getId()))
                .orElseThrow(() -> new DomainException(OrderErrors.CART_ITEM_NOT_FOUND, "cart item not found: " + itemId));
        if (qty <= 0) {
            items.delete(item);
        } else {
            item.changeQty(qty);
        }
        return view(clientUserId);
    }

    @Transactional
    public CartView removeItem(UUID clientUserId, UUID itemId) {
        Cart cart = requireCart(clientUserId);
        items.findById(itemId)
                .filter(i -> i.getCartId().equals(cart.getId()))
                .ifPresent(items::delete);
        return view(clientUserId);
    }

    @Transactional
    public void clear(UUID cartId) {
        items.deleteByCartId(cartId);
    }

    private Cart requireCart(UUID clientUserId) {
        return carts.findByClientUserId(clientUserId)
                .orElseThrow(() -> new DomainException(OrderErrors.CART_EMPTY, "no cart for client " + clientUserId));
    }

    public record CartView(UUID cartId, UUID cityId, List<CartItem> items) {

        public long subtotalMinor() {
            return items.stream().mapToLong(i -> i.getUnitSalePriceMinor() * i.getQty()).sum();
        }
    }
}
