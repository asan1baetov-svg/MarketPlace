package greenecomall.order.catalog;

import greenecomall.common.domain.DomainException;
import greenecomall.order.OrderErrors;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Реализация {@link CatalogClient} через {@link RestClient}. Ошибки catalog-service переводятся
 * в доменные коды order-service: 409 на резерве → {@link OrderErrors#STOCK_INSUFFICIENT},
 * прочее/сеть → {@link OrderErrors#CATALOG_UNAVAILABLE}.
 */
@Component
public class RestCatalogClient implements CatalogClient {

    private final RestClient http;

    public RestCatalogClient(RestClient catalogRestClient) {
        this.http = catalogRestClient;
    }

    @Override
    public StorefrontProduct storefrontProduct(UUID productId, UUID cityId) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/catalog/products/{id}")
                            .queryParam("cityId", cityId)
                            .build(productId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            throw new DomainException(OrderErrors.CART_ITEM_NOT_FOUND,
                                    "product not found in catalog: " + productId);
                        }
                        throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE,
                                "catalog product lookup failed for " + productId + ": HTTP " + res.getStatusCode());
                    })
                    .body(StorefrontProduct.class);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE,
                    "catalog product lookup failed for " + productId, e);
        }
    }

    @Override
    public PriceView price(UUID productId, UUID cityId) {
        try {
            return http.get()
                    .uri(uri -> uri.path("/internal/catalog/price")
                            .queryParam("productId", productId)
                            .queryParam("cityId", cityId)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            throw new DomainException(OrderErrors.PRODUCT_UNAVAILABLE,
                                    "product is no longer on sale in this city: " + productId);
                        }
                        throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE,
                                "catalog price resolve failed for product " + productId + ": HTTP " + res.getStatusCode());
                    })
                    .body(PriceView.class);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE,
                    "catalog price resolve failed for product " + productId, e);
        }
    }

    @Override
    public ShopView shop(UUID shopId) {
        try {
            return http.get()
                    .uri("/internal/catalog/shops/{id}", shopId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            throw new DomainException(OrderErrors.SHOP_NOT_FOUND, "shop not found: " + shopId);
                        }
                        throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE,
                                "catalog shop lookup failed for " + shopId + ": HTTP " + res.getStatusCode());
                    })
                    .body(ShopView.class);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE, "catalog shop lookup failed for " + shopId, e);
        }
    }

    @Override
    public void reserve(UUID productId, int qty) {
        stockOp("/internal/catalog/stock/reserve", productId, qty, true);
    }

    @Override
    public void release(UUID productId, int qty) {
        stockOp("/internal/catalog/stock/release", productId, qty, false);
    }

    @Override
    public void commit(UUID productId, int qty) {
        stockOp("/internal/catalog/stock/commit", productId, qty, false);
    }

    private void stockOp(String path, UUID productId, int qty, boolean failOnConflict) {
        try {
            http.post()
                    .uri(path)
                    .body(Map.of("productId", productId, "qty", qty))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (failOnConflict && res.getStatusCode().value() == 409) {
                            throw new DomainException(OrderErrors.STOCK_INSUFFICIENT,
                                    "not enough stock for product " + productId);
                        }
                        throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE,
                                "catalog stock op " + path + " failed for product " + productId
                                        + ": HTTP " + res.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE,
                    "catalog stock op " + path + " failed for product " + productId, e);
        }
    }
}
