package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.ProductUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Владелец магазина берётся из access-JWT, а не из тела. */
public record ProductCreateRequest(
        @NotNull UUID categoryId, @NotBlank @Size(max = 300) String name, @Size(max = 5000) String description,
        ProductUnit unit, @PositiveOrZero long costPriceMinor, @NotBlank String currency) {
}
