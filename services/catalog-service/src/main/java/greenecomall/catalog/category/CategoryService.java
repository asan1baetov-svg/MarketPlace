package greenecomall.catalog.category;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.Category;
import greenecomall.catalog.repo.CategoryRepository;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.common.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return categories.findAllByOrderBySortAsc();
    }

    @Transactional
    public UUID create(UUID parentId, String name, String slug, int sort) {
        Category parent = parentId == null ? null : requireCategory(parentId);
        if (categories.existsBySlug(slug)) {
            throw new DomainException(CatalogErrors.CATEGORY_SLUG_TAKEN, "slug already exists: " + slug);
        }
        return categories.save(new Category(parent, name, slug, sort)).getId();
    }

    @Transactional
    public void update(UUID id, UUID parentId, String name, String slug, int sort) {
        Category category = requireCategory(id);
        Category parent = parentId == null ? null : requireCategory(parentId);
        if (categories.existsBySlugAndIdNot(slug, id)) {
            throw new DomainException(CatalogErrors.CATEGORY_SLUG_TAKEN, "slug already exists: " + slug);
        }
        category.update(parent, name, slug, sort);
    }

    @Transactional
    public void delete(UUID id) {
        requireCategory(id);
        if (categories.existsByParentId(id)) {
            throw new DomainException(CatalogErrors.CATEGORY_HAS_DEPENDENTS, "category has subcategories");
        }
        if (products.existsByCategoryId(id)) {
            throw new DomainException(CatalogErrors.CATEGORY_HAS_DEPENDENTS, "category has products");
        }
        categories.deleteById(id);
    }

    private Category requireCategory(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.CATEGORY_NOT_FOUND, "category not found: " + id));
    }
}
