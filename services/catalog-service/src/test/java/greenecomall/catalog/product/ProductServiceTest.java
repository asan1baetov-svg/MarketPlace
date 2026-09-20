package greenecomall.catalog.product;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.Category;
import greenecomall.catalog.domain.City;
import greenecomall.catalog.domain.Country;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductStock;
import greenecomall.catalog.domain.ProductUnit;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.pricing.MarkupService;
import greenecomall.catalog.repo.CategoryRepository;
import greenecomall.catalog.repo.ProductImageRepository;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.catalog.repo.ProductStockRepository;
import greenecomall.catalog.shop.ShopService;
import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository products;
    @Mock private ProductImageRepository images;
    @Mock private ProductStockRepository stocks;
    @Mock private CategoryRepository categories;
    @Mock private ShopService shopService;
    @Mock private MarkupService markupService;
    @Mock private DomainEventPublisher events;

    private ProductService service;
    private Shop shop;
    private City city;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, images, stocks, categories, shopService, markupService, events);
        Country country = new Country("Kyrgyzstan", "KG");
        city = new City(country, "Bishkek", null, null, null);
        ReflectionTestUtils.setField(city, "id", UUID.randomUUID());
        shop = new Shop(UUID.randomUUID(), "Eco Shop", null, country, city);
        ReflectionTestUtils.setField(shop, "id", UUID.randomUUID());
        shop.approve();
        Category category = new Category(null, "Vegetables", "vegetables", 0);
        product = new Product(shop, category, "Carrot", null, ProductUnit.KG, 10_000, "KGS");
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.publish();
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
    }

    @Test
    void publishedProductOfActiveShop_isSellableInItsCity() {
        assertThat(service.requireSellable(product.getId(), city.getId())).isSameAs(product);
        assertThat(service.requireSellable(product.getId(), null)).isSameAs(product);
    }

    @Test
    void draftProduct_isNotSellable() {
        product.submitForModeration();
        assertNotSellable(city.getId());
    }

    @Test
    void productOfSuspendedShop_isNotSellable() {
        shop.suspend("fraud");
        assertNotSellable(city.getId());
    }

    @Test
    void productFromAnotherCity_isNotSellable() {
        assertNotSellable(UUID.randomUUID());
    }

    @Test
    void stockCannotDropBelowReserved() {
        ProductStock stock = new ProductStock(product, 10);
        ReflectionTestUtils.setField(stock, "reservedQuantity", 4);
        when(stocks.findByProductId(product.getId())).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.setStock(product.getId(), shop.getOwnerUserId(), 3))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(CatalogErrors.STOCK_INSUFFICIENT);
        assertThat(stock.getQuantity()).isEqualTo(10);
    }

    private void assertNotSellable(UUID cityId) {
        assertThatThrownBy(() -> service.requireSellable(product.getId(), cityId))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(CatalogErrors.PRODUCT_NOT_FOUND);
    }
}
