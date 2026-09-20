package greenecomall.order.web;

import greenecomall.order.promo.PromocodeService;
import greenecomall.order.web.dto.IdResponse;
import greenecomall.order.web.dto.PromocodeCreateRequest;
import greenecomall.order.web.dto.PromocodeResponse;
import greenecomall.order.web.dto.PromocodeUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/admin/promocodes")
public class PromocodeAdminController {

    private final PromocodeService promocodes;

    public PromocodeAdminController(PromocodeService promocodes) {
        this.promocodes = promocodes;
    }

    @GetMapping
    public List<PromocodeResponse> list() {
        return promocodes.list().stream().map(PromocodeResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@Valid @RequestBody PromocodeCreateRequest request) {
        return new IdResponse(promocodes.create(
                request.code(), request.type(), request.value(), request.fundedBy(),
                request.validFrom(), request.validTo(), request.usageLimit()));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID id, @Valid @RequestBody PromocodeUpdateRequest request) {
        promocodes.update(id, request.value(), request.validFrom(), request.validTo(),
                request.usageLimit(), request.active());
    }
}
