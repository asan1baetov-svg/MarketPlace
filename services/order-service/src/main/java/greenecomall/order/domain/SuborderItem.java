package greenecomall.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Позиция suborder — снимок цен на момент оформления (наценка/себестоимость могут потом
 * измениться, см. docs/ARCHITECTURE.md §3.3).
 */
@Entity
@Table(name = "suborder_items", indexes = @Index(name = "idx_suborder_items_suborder", columnList = "suborder_id"))
public class SuborderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "suborder_id", nullable = false, updatable = false)
    private UUID suborderId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "name_snapshot", nullable = false, length = 300)
    private String nameSnapshot;

    @Column(nullable = false)
    private int qty;

    @Column(name = "cost_price_minor", nullable = false)
    private long costPriceMinor;

    @Column(name = "sale_price_minor", nullable = false)
    private long salePriceMinor;

    @Column(name = "markup_percent_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal markupPercentSnapshot;

    protected SuborderItem() {
    }

    public SuborderItem(UUID suborderId, UUID productId, String nameSnapshot, int qty,
                        long costPriceMinor, long salePriceMinor, BigDecimal markupPercentSnapshot) {
        this.suborderId = suborderId;
        this.productId = productId;
        this.nameSnapshot = nameSnapshot;
        this.qty = qty;
        this.costPriceMinor = costPriceMinor;
        this.salePriceMinor = salePriceMinor;
        this.markupPercentSnapshot = markupPercentSnapshot;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSuborderId() {
        return suborderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getNameSnapshot() {
        return nameSnapshot;
    }

    public int getQty() {
        return qty;
    }

    public long getCostPriceMinor() {
        return costPriceMinor;
    }

    public long getSalePriceMinor() {
        return salePriceMinor;
    }

    public BigDecimal getMarkupPercentSnapshot() {
        return markupPercentSnapshot;
    }
}
