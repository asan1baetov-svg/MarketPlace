package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ZoneCreateRequest(@NotNull UUID cityId, @NotBlank String name, Integer radiusM) {
}
