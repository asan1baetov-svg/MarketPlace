package greenecomall.common.domain;

import java.util.UUID;

/**
 * Гео-привязка сущности. Город обязателен для фильтрации каталога,
 * страна дублируется для отчётности и региональных правил (наценка и т.п.).
 */
public record GeoRef(UUID countryId, UUID cityId) {
}
