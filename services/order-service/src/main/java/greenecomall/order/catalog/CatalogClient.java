package greenecomall.order.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Синхронный контракт к catalog-service (служебное API {@code /internal/catalog/**}):
 * резолв цены при checkout и условный резерв/релиз/коммит остатков.
 */
public interface CatalogClient {

    /** Публичный вид товара в витрине города: магазин, название, цена продажи. */
    StorefrontProduct storefrontProduct(UUID productId, UUID cityId);

    /** Резолв себестоимости, наценки и цены продажи товара для города доставки. */
    PriceView price(UUID productId, UUID cityId);

    /** Условный резерв: бросает {@code DomainException(STOCK_INSUFFICIENT)}, если остатка не хватает. */
    void reserve(UUID productId, int qty);

    void release(UUID productId, int qty);

    void commit(UUID productId, int qty);

    /** Владелец магазина — для проверки доступа к кабинету магазина по {@code sub} токена. */
    ShopView shop(UUID shopId);

    record PriceView(UUID productId, long costPriceMinor, long salePriceMinor, BigDecimal markupPercent, String currency) {
    }

    record ShopView(UUID id, UUID ownerUserId, String status) {
    }

    record StorefrontProduct(UUID id, UUID shopId, String name, long salePriceMinor, String currency) {
    }
}
