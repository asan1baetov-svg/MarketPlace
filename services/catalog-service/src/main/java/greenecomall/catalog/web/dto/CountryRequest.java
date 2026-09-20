package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CountryRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter ISO code") String isoCode) {
}
