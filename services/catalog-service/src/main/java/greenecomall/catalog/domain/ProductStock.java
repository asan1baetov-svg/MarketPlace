package greenecomall.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Остатки товара. {@code reservedQuantity} растёт при checkout, гасится оплатой (commit) или отменой (release). */
@Entity
@Table(name = "product_stock")
public class ProductStock {

    @Id
    private UUID productId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    protected ProductStock() {
    }

    public ProductStock(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.reservedQuantity = 0;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int availableQuantity() {
        return quantity - reservedQuantity;
    }

    public UUID getProductId() {
        return productId;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }
}
