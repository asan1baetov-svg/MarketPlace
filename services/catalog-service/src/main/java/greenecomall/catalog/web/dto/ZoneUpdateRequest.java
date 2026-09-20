package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ZoneUpdateRequest(@NotBlank String name, Integer radiusM) {
}
