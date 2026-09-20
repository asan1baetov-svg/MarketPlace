package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.Category;

import java.util.UUID;

public record CategoryResponse(UUID id, UUID parentId, String name, String slug, int sort) {

    public static CategoryResponse from(Category category) {
        UUID parentId = category.getParent() != null ? category.getParent().getId() : null;
        return new CategoryResponse(category.getId(), parentId, category.getName(), category.getSlug(),
                category.getSort());
    }
}
