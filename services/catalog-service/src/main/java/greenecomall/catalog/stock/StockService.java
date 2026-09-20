package greenecomall.catalog.stock;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.repo.ProductStockRepository;
import greenecomall.common.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Внутренний API остатков для order-service: условный резерв при checkout, release при отмене,
 * commit при оплате. См. docs/ARCHITECTURE.md §2.3 R2 (гонки на резерве).
 */
@Service
public class StockService {

    private final ProductStockRepository stocks;

    public StockService(ProductStockRepository stocks) {
        this.stocks = stocks;
    }

    @Transactional
    public void reserve(UUID productId, int qty) {
        int updated = stocks.reserve(productId, qty);
        if (updated == 0) {
            throw new DomainException(CatalogErrors.STOCK_INSUFFICIENT,
                    "not enough available stock for product " + productId);
        }
    }

    @Transactional
    public void release(UUID productId, int qty) {
        stocks.release(productId, qty);
    }

    @Transactional
    public void commit(UUID productId, int qty) {
        stocks.commit(productId, qty);
    }
}
