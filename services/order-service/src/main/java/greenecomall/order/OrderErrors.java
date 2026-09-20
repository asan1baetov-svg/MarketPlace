package greenecomall.order;

/** Коды доменных ошибок order-service (тот же приём, что {@code CatalogErrors} в catalog-service). */
public final class OrderErrors {

    public static final String CART_EMPTY = "order.cart_empty";
    public static final String CART_CITY_MISMATCH = "order.cart_city_mismatch";
    public static final String CART_ITEM_NOT_FOUND = "order.cart_item_not_found";

    public static final String ORDER_NOT_FOUND = "order.order_not_found";
    public static final String ORDER_STATUS_INVALID = "order.order_status_invalid";
    public static final String ORDER_FORBIDDEN = "order.order_forbidden";

    public static final String SUBORDER_NOT_FOUND = "order.suborder_not_found";
    public static final String SUBORDER_STATUS_INVALID = "order.suborder_status_invalid";
    public static final String SUBORDER_FORBIDDEN = "order.suborder_forbidden";
    public static final String SHOP_NOT_FOUND = "order.shop_not_found";

    public static final String PROMOCODE_NOT_FOUND = "order.promocode_not_found";
    public static final String PROMOCODE_INVALID = "order.promocode_invalid";
    public static final String PROMOCODE_CODE_TAKEN = "order.promocode_code_taken";

    public static final String CATALOG_UNAVAILABLE = "order.catalog_unavailable";
    public static final String STOCK_INSUFFICIENT = "order.stock_insufficient";
    public static final String PRODUCT_UNAVAILABLE = "order.product_unavailable";
    public static final String CURRENCY_MISMATCH = "order.currency_mismatch";

    private OrderErrors() {
    }
}
