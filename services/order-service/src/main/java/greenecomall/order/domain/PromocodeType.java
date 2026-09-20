package greenecomall.order.domain;

public enum PromocodeType {
    /** {@code value} — процент скидки (0..100). */
    PERCENT,
    /** {@code value} — фиксированная скидка в минорных единицах валюты заказа. */
    FIXED
}
