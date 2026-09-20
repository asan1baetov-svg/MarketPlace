package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CategoryRequest(UUID parentId, @NotBlank String name, @NotBlank String slug, int sort) {
}
