package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ImageAddRequest(@NotBlank String url) {
}
