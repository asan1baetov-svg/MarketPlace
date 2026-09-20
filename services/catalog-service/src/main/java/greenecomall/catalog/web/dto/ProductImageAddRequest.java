package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ProductImageAddRequest(@NotBlank String url) {
}
