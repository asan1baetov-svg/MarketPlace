package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CityCreateRequest(
        @NotNull UUID countryId, @NotBlank String name, BigDecimal lat, BigDecimal lon, String timezone) {
}
