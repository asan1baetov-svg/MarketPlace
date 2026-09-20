package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** Страну города не меняем — переезд города в другую страну здесь не поддерживается. */
public record CityUpdateRequest(@NotBlank String name, BigDecimal lat, BigDecimal lon, String timezone) {
}
