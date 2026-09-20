package greenecomall.catalog.web;

import greenecomall.catalog.pricing.MarkupService;
import greenecomall.catalog.web.dto.IdResponse;
import greenecomall.catalog.web.dto.MarkupRuleRequest;
import greenecomall.catalog.web.dto.MarkupRuleResponse;
import greenecomall.catalog.web.dto.MarkupRuleUpdateRequest;
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

@RestController
@RequestMapping("/admin/markup-rules")
public class MarkupAdminController {

    private final MarkupService markupService;

    public MarkupAdminController(MarkupService markupService) {
        this.markupService = markupService;
    }

    @GetMapping
    public List<MarkupRuleResponse> list() {
        return markupService.list().stream().map(MarkupRuleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@Valid @RequestBody MarkupRuleRequest request) {
        UUID id = markupService.create(request.scope(), request.categoryId(), request.shopId(), request.productId(),
                request.countryId(), request.cityId(), request.markupPercent(), request.priority(), request.active());
        return new IdResponse(id);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable UUID id, @Valid @RequestBody MarkupRuleUpdateRequest request) {
        markupService.update(id, request.markupPercent(), request.priority(), request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        markupService.delete(id);
    }
}
