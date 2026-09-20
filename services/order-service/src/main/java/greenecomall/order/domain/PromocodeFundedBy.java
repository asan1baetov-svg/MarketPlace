package greenecomall.order.domain;

/**
 * Кто финансирует скидку (см. docs/ARCHITECTURE.md §8.2 вопрос 7).
 * {@code PLATFORM} — уменьшается комиссия платформы; {@code SHOP} — уменьшается выплата магазину.
 */
public enum PromocodeFundedBy {
    PLATFORM,
    SHOP
}
