package greenecomall.courier.web;

import greenecomall.common.web.PageResponse;
import greenecomall.courier.courier.CourierService;
import greenecomall.courier.dispatch.DispatchService;
import greenecomall.courier.domain.Courier;
import greenecomall.courier.web.dto.CourierDtos.CourierResponse;
import greenecomall.courier.web.dto.CourierDtos.ManualAssignRequest;
import greenecomall.courier.web.dto.CourierDtos.ReasonRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Админ: модерация курьеров, мониторинг, ручное назначение. */
@RestController
public class AdminCourierController {

    private final CourierService courierService;
    private final DispatchService dispatch;

    public AdminCourierController(CourierService courierService, DispatchService dispatch) {
        this.courierService = courierService;
        this.dispatch = dispatch;
    }

    @GetMapping("/admin/couriers")
    public PageResponse<CourierResponse> search(@RequestParam(required = false) UUID cityId,
                                                @RequestParam(required = false) Courier.Moderation moderation,
                                                Pageable pageable) {
        Page<Courier> page = courierService.search(cityId, moderation, pageable);
        return PageResponse.of(page.getContent().stream().map(CourierResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @PostMapping("/admin/couriers/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID id) {
        courierService.approve(id);
    }

    @PostMapping("/admin/couriers/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest request) {
        courierService.reject(id, request == null ? null : request.reason());
    }

    @PostMapping("/admin/assignments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@Valid @RequestBody ManualAssignRequest request) {
        dispatch.assignManually(request.suborderId(), request.courierId());
    }

    /** Свободные курьеры города: админ-панель ({@code /admin}) и другие сервисы ({@code /internal}). */
    @GetMapping({"/internal/couriers/available", "/admin/couriers/available"})
    public List<CourierResponse> available(@RequestParam UUID cityId) {
        return dispatch.available(cityId).stream().map(CourierResponse::from).toList();
    }
}
