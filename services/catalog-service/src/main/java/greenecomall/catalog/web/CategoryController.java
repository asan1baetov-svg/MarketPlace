package greenecomall.catalog.web;

import greenecomall.catalog.category.CategoryService;
import greenecomall.catalog.web.dto.CategoryRequest;
import greenecomall.catalog.web.dto.CategoryResponse;
import greenecomall.catalog.web.dto.IdResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Публичное дерево категорий + admin CRUD. Дерево отдаётся плоским списком (клиент строит по {@code parentId}). */
@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/catalog/categories")
    public List<CategoryResponse> list() {
        return categoryService.list().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping("/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@Valid @RequestBody CategoryRequest request) {
        UUID id = categoryService.create(request.parentId(), request.name(), request.slug(), request.sort());
        return new IdResponse(id);
    }

    @PutMapping("/admin/categories/{id}")
    public void update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        categoryService.update(id, request.parentId(), request.name(), request.slug(), request.sort());
    }

    @DeleteMapping("/admin/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        categoryService.delete(id);
    }
}
