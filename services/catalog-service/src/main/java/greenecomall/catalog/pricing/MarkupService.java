package greenecomall.catalog.pricing;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.Category;
import greenecomall.catalog.domain.City;
import greenecomall.catalog.domain.Country;
import greenecomall.catalog.domain.MarkupRule;
import greenecomall.catalog.domain.MarkupScope;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.repo.CategoryRepository;
import greenecomall.catalog.repo.CityRepository;
import greenecomall.catalog.repo.CountryRepository;
import greenecomall.catalog.repo.MarkupRuleRepository;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.catalog.repo.ShopRepository;
import greenecomall.common.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Резолв цены по приоритету PRODUCT &gt; SHOP &gt; CATEGORY_REGION &gt; CATEGORY &gt; REGION
 * &gt; GLOBAL (см. docs/ARCHITECTURE.md §2.2) + CRUD правил наценки.
 *
 * <p>Redis-кэш резолва (ключ {@code price:{productId}:{cityId}}) из архитектуры сюда сознательно
 * не добавлен — это чистая оптимизация поверх уже корректного резолва, отложена на потом.
 */
@Service
public class MarkupService {

    private final MarkupRuleRepository rules;
    private final CategoryRepository categories;
    private final ShopRepository shops;
    private final ProductRepository products;
    private final CountryRepository countries;
    private final CityRepository cities;

    public MarkupService(MarkupRuleRepository rules, CategoryRepository categories, ShopRepository shops,
                         ProductRepository products, CountryRepository countries, CityRepository cities) {
        this.rules = rules;
        this.categories = categories;
        this.shops = shops;
        this.products = products;
        this.countries = countries;
        this.cities = cities;
    }

    @Transactional(readOnly = true)
    public PriceResolution resolve(Product product, UUID cityId) {
        BigDecimal percent = resolveMarkupPercent(product, cityId);
        long sale = applyMarkup(product.getCostPriceMinor(), percent);
        return new PriceResolution(product.getId(), product.getCostPriceMinor(), sale, percent, product.getCurrency());
    }

    @Transactional(readOnly = true)
    public List<MarkupRule> list() {
        return rules.findAll();
    }

    @Transactional
    public UUID create(MarkupScope scope, UUID categoryId, UUID shopId, UUID productId, UUID countryId,
                       UUID cityId, BigDecimal markupPercent, int priority, boolean active) {
        validateScopeFields(scope, categoryId, shopId, productId, cityId);
        Category category = categoryId == null ? null : requireCategory(categoryId);
        Shop shop = shopId == null ? null : requireShop(shopId);
        Product product = productId == null ? null : requireProduct(productId);
        Country country = countryId == null ? null : requireCountry(countryId);
        City city = cityId == null ? null : requireCity(cityId);
        return rules.save(new MarkupRule(scope, category, shop, product, country, city, markupPercent, priority, active))
                .getId();
    }

    @Transactional
    public void update(UUID id, BigDecimal markupPercent, int priority, boolean active) {
        MarkupRule rule = rules.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.MARKUP_RULE_NOT_FOUND, "rule not found: " + id));
        rule.update(markupPercent, priority, active);
    }

    @Transactional
    public void delete(UUID id) {
        if (!rules.existsById(id)) {
            throw new DomainException(CatalogErrors.MARKUP_RULE_NOT_FOUND, "rule not found: " + id);
        }
        rules.deleteById(id);
    }

    private BigDecimal resolveMarkupPercent(Product product, UUID cityId) {
        return findByScope(MarkupScope.PRODUCT,
                () -> rules.findFirstByScopeAndProductIdAndActiveTrue(MarkupScope.PRODUCT, product.getId()))
                .or(() -> findByScope(MarkupScope.SHOP, () -> rules.findFirstByScopeAndShopIdAndActiveTrueOrderByPriorityDesc(
                        MarkupScope.SHOP, product.getShop().getId())))
                .or(() -> findByScope(MarkupScope.CATEGORY_REGION,
                        () -> rules.findFirstByScopeAndCategoryIdAndCityIdAndActiveTrueOrderByPriorityDesc(
                                MarkupScope.CATEGORY_REGION, product.getCategory().getId(), cityId)))
                .or(() -> findByScope(MarkupScope.CATEGORY,
                        () -> rules.findFirstByScopeAndCategoryIdAndActiveTrueOrderByPriorityDesc(
                                MarkupScope.CATEGORY, product.getCategory().getId())))
                .or(() -> findByScope(MarkupScope.REGION,
                        () -> rules.findFirstByScopeAndCityIdAndActiveTrueOrderByPriorityDesc(MarkupScope.REGION, cityId)))
                .or(() -> findByScope(MarkupScope.GLOBAL,
                        () -> rules.findFirstByScopeAndActiveTrueOrderByPriorityDesc(MarkupScope.GLOBAL)))
                .orElseThrow(() -> new DomainException(
                        CatalogErrors.MARKUP_RULE_NOT_FOUND, "no markup rule resolves for product " + product.getId()));
    }

    private Optional<BigDecimal> findByScope(MarkupScope scope, java.util.function.Supplier<Optional<MarkupRule>> lookup) {
        return lookup.get().map(MarkupRule::getMarkupPercent);
    }

    /**
     * Цена продажи округляется вверх до целой единицы валюты: эквайринг принимает только целые сомы,
     * а округление вверх не уменьшает наценку платформы.
     */
    private static long applyMarkup(long costMinor, BigDecimal percent) {
        BigDecimal factor = BigDecimal.ONE.add(percent.divide(BigDecimal.valueOf(100)));
        return BigDecimal.valueOf(costMinor).multiply(factor).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING)
                .longValueExact() * 100;
    }

    private void validateScopeFields(MarkupScope scope, UUID categoryId, UUID shopId, UUID productId, UUID cityId) {
        boolean valid = switch (scope) {
            case PRODUCT -> productId != null;
            case SHOP -> shopId != null;
            case CATEGORY -> categoryId != null;
            case REGION -> cityId != null;
            case CATEGORY_REGION -> categoryId != null && cityId != null;
            case GLOBAL -> true;
        };
        if (!valid) {
            throw new DomainException(CatalogErrors.MARKUP_RULE_INVALID,
                    "scope " + scope + " requires the matching id field(s)");
        }
    }

    private Category requireCategory(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.CATEGORY_NOT_FOUND, "category not found: " + id));
    }

    private Shop requireShop(UUID id) {
        return shops.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.SHOP_NOT_FOUND, "shop not found: " + id));
    }

    private Product requireProduct(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.PRODUCT_NOT_FOUND, "product not found: " + id));
    }

    private Country requireCountry(UUID id) {
        return countries.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.COUNTRY_NOT_FOUND, "country not found: " + id));
    }

    private City requireCity(UUID id) {
        return cities.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.CITY_NOT_FOUND, "city not found: " + id));
    }
}
