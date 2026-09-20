package greenecomall.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Позиция корзины. {@code unitSalePriceMinor} — цена с наценкой на момент добавления
 * (индикативная; финальный снимок берётся при checkout).
 */
@Entity
@Table(name = "cart_items", indexes = @Index(name = "idx_cart_items_cart", columnList = "cart_id"))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cart_id", nullable = false, updatable = false)
    private UUID cartId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "shop_id", nullable = false, updatable = false)
    private UUID shopId;

    @Column(name = "product_name", nullable = false, length = 300)
    private String productName;

    @Column(nullable = false)
    private int qty;

    @Column(name = "unit_sale_price_minor", nullable = false)
    private long unitSalePriceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected CartItem() {
    }

    public CartItem(UUID cartId, UUID productId, UUID shopId, String productName, int qty,
                    long unitSalePriceMinor, String currency) {
        this.cartId = cartId;
        this.productId = productId;
        this.shopId = shopId;
        this.productName = productName;
        this.qty = qty;
        this.unitSalePriceMinor = unitSalePriceMinor;
        this.currency = currency;
    }

    public void changeQty(int qty) {
        this.qty = qty;
    }

    public void refreshPrice(long unitSalePriceMinor, String currency) {
        this.unitSalePriceMinor = unitSalePriceMinor;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCartId() {
        return cartId;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getShopId() {
        return shopId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQty() {
        return qty;
    }

    public long getUnitSalePriceMinor() {
        return unitSalePriceMinor;
    }

    public String getCurrency() {
        return currency;
    }
}
