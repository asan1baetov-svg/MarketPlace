package greenecomall.catalog.pricing;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.Category;
import greenecomall.catalog.domain.City;
import greenecomall.catalog.domain.Country;
import greenecomall.catalog.domain.MarkupRule;
import greenecomall.catalog.domain.MarkupScope;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductUnit;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.repo.CategoryRepository;
import greenecomall.catalog.repo.CityRepository;
import greenecomall.catalog.repo.CountryRepository;
import greenecomall.catalog.repo.MarkupRuleRepository;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.catalog.repo.ShopRepository;
import greenecomall.common.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkupServiceTest {

    @Mock
    private MarkupRuleRepository rules;
    @Mock
    private CategoryRepository categories;
    @Mock
    private ShopRepository shops;
    @Mock
    private ProductRepository products;
    @Mock
    private CountryRepository countries;
    @Mock
    private CityRepository cities;

    private MarkupService markupService;
    private Product product;
    private UUID cityId;

    @BeforeEach
    void setUp() {
        markupService = new MarkupService(rules, categories, shops, products, countries, cities);

        Country country = new Country("Kyrgyzstan", "KG");
        City city = new City(country, "Bishkek", null, null, null);
        ReflectionTestUtils.setField(city, "id", UUID.randomUUID());
        cityId = city.getId();
        Shop shop = new Shop(UUID.randomUUID(), "Eco Shop", null, country, city);
        ReflectionTestUtils.setField(shop, "id", UUID.randomUUID());
        Category category = new Category(null, "Vegetables", "vegetables", 0);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        product = new Product(shop, category, "Carrot", null, ProductUnit.KG, 10_000, "KGS");
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    }

    @Test
    void resolve_prefersProductRuleOverEverythingElse() {
        MarkupRule productRule = rule(BigDecimal.valueOf(50));
        when(rules.findFirstByScopeAndProductIdAndActiveTrue(MarkupScope.PRODUCT, product.getId()))
                .thenReturn(Optional.of(productRule));

        PriceResolution resolved = markupService.resolve(product, cityId);

        assertThat(resolved.markupPercent()).isEqualByComparingTo("50");
        assertThat(resolved.salePriceMinor()).isEqualTo(15_000); // 10_000 * 1.5
    }

    @Test
    void resolve_fallsBackToShopRule_whenNoProductRule() {
        when(rules.findFirstByScopeAndProductIdAndActiveTrue(MarkupScope.PRODUCT, product.getId()))
                .thenReturn(Optional.empty());
        when(rules.findFirstByScopeAndShopIdAndActiveTrueOrderByPriorityDesc(MarkupScope.SHOP, product.getShop().getId()))
                .thenReturn(Optional.of(rule(BigDecimal.valueOf(30))));

        PriceResolution resolved = markupService.resolve(product, cityId);

        assertThat(resolved.markupPercent()).isEqualByComparingTo("30");
    }

    @Test
    void resolve_fallsBackToGlobalRule_whenNothingElseMatches() {
        when(rules.findFirstByScopeAndProductIdAndActiveTrue(MarkupScope.PRODUCT, product.getId()))
                .thenReturn(Optional.empty());
        when(rules.findFirstByScopeAndShopIdAndActiveTrueOrderByPriorityDesc(MarkupScope.SHOP, product.getShop().getId()))
                .thenReturn(Optional.empty());
        when(rules.findFirstByScopeAndCategoryIdAndCityIdAndActiveTrueOrderByPriorityDesc(
                MarkupScope.CATEGORY_REGION, product.getCategory().getId(), cityId)).thenReturn(Optional.empty());
        when(rules.findFirstByScopeAndCategoryIdAndActiveTrueOrderByPriorityDesc(
                MarkupScope.CATEGORY, product.getCategory().getId())).thenReturn(Optional.empty());
        when(rules.findFirstByScopeAndCityIdAndActiveTrueOrderByPriorityDesc(MarkupScope.REGION, cityId))
                .thenReturn(Optional.empty());
        when(rules.findFirstByScopeAndActiveTrueOrderByPriorityDesc(MarkupScope.GLOBAL))
                .thenReturn(Optional.of(rule(BigDecimal.valueOf(20))));

        PriceResolution resolved = markupService.resolve(product, cityId);

        assertThat(resolved.markupPercent()).isEqualByComparingTo("20");
        assertThat(resolved.salePriceMinor()).isEqualTo(12_000);
    }

    @Test
    void resolve_withNoRuleAtAll_throws() {
        when(rules.findFirstByScopeAndActiveTrueOrderByPriorityDesc(MarkupScope.GLOBAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> markupService.resolve(product, cityId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.MARKUP_RULE_NOT_FOUND);
    }

    @Test
    void create_categoryScopeWithoutCategoryId_isRejected() {
        assertThatThrownBy(() -> markupService.create(
                MarkupScope.CATEGORY, null, null, null, null, null, BigDecimal.TEN, 0, true))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.MARKUP_RULE_INVALID);
    }

    @Test
    void create_globalScope_needsNoIds() {
        when(rules.save(org.mockito.ArgumentMatchers.any(MarkupRule.class))).thenAnswer(inv -> {
            MarkupRule saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        UUID id = markupService.create(MarkupScope.GLOBAL, null, null, null, null, null, BigDecimal.TEN, 0, true);

        assertThat(id).isNotNull();
    }

    private MarkupRule rule(BigDecimal percent) {
        return new MarkupRule(MarkupScope.PRODUCT, null, null, null, null, null, percent, 0, true);
    }
}
