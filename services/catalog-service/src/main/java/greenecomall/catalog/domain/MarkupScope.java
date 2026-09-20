package greenecomall.catalog.domain;

/** Приоритет резолва (выше в списке = выше приоритет): PRODUCT > SHOP > CATEGORY_REGION > CATEGORY > REGION > GLOBAL. */
public enum MarkupScope {
    PRODUCT, SHOP, CATEGORY_REGION, CATEGORY, REGION, GLOBAL
}
