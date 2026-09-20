package greenecomall.catalog;

/** Коды доменных ошибок catalog-service (см. {@code AuthErrors} в auth-service — тот же приём). */
public final class CatalogErrors {

    public static final String COUNTRY_CODE_TAKEN = "catalog.country_code_taken";
    public static final String COUNTRY_NOT_FOUND = "catalog.country_not_found";
    public static final String CITY_NOT_FOUND = "catalog.city_not_found";
    public static final String ZONE_NOT_FOUND = "catalog.zone_not_found";
    public static final String GEO_HAS_DEPENDENTS = "catalog.geo_has_dependents";

    public static final String CATEGORY_NOT_FOUND = "catalog.category_not_found";
    public static final String CATEGORY_SLUG_TAKEN = "catalog.category_slug_taken";
    public static final String CATEGORY_HAS_DEPENDENTS = "catalog.category_has_dependents";

    public static final String SHOP_NOT_FOUND = "catalog.shop_not_found";
    public static final String SHOP_NOT_ACTIVE = "catalog.shop_not_active";
    public static final String SHOP_FORBIDDEN = "catalog.shop_forbidden";
    public static final String SHOP_STATUS_INVALID = "catalog.shop_status_invalid";

    public static final String PRODUCT_NOT_FOUND = "catalog.product_not_found";
    public static final String PRODUCT_FORBIDDEN = "catalog.product_forbidden";
    public static final String PRODUCT_STATUS_INVALID = "catalog.product_status_invalid";

    public static final String STOCK_NOT_FOUND = "catalog.stock_not_found";
    public static final String STOCK_INSUFFICIENT = "catalog.stock_insufficient";

    public static final String MARKUP_RULE_NOT_FOUND = "catalog.markup_rule_not_found";
    public static final String MARKUP_RULE_INVALID = "catalog.markup_rule_invalid";

    public static final String FORBIDDEN = "catalog.forbidden";

    public static final String REVIEW_NOT_FOUND = "catalog.review_not_found";
    public static final String REVIEW_NOT_ALLOWED = "catalog.review_not_allowed";
    public static final String ORDER_UNAVAILABLE = "catalog.order_unavailable";
    public static final String IMAGE_NOT_FOUND = "catalog.image_not_found";
    public static final String IMAGE_INVALID = "catalog.image_invalid";

    private CatalogErrors() {
    }
}
