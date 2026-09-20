package greenecomall.courier.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.security.Authz;
import greenecomall.courier.courier.CourierService;
import greenecomall.courier.dispatch.DispatchService;
import greenecomall.courier.domain.DeliveryJob;
import greenecomall.courier.web.dto.CourierDtos.DeliveryResponse;
import greenecomall.courier.web.dto.CourierDtos.FailRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Смена статуса доставки курьером: принял → забрал у магазина → в пути → доставлен / не доставлен.
 * Идентификатор доставки = {@code suborderId}; курьер — из access-JWT, то, что доставка назначена
 * именно ему, проверяет {@link DispatchService}.
 */
@RestController
@RequestMapping("/assignments/{suborderId}")
public class AssignmentController {

    private final DispatchService dispatch;
    private final CourierService couriers;

    public AssignmentController(DispatchService dispatch, CourierService couriers) {
        this.dispatch = dispatch;
        this.couriers = couriers;
    }

    @GetMapping
    public DeliveryResponse get(@PathVariable UUID suborderId, AuthPrincipal principal) {
        DeliveryJob job = dispatch.get(suborderId);
        if (!Authz.isAdmin(principal) && !courierId(principal).equals(job.getCourierId())) {
            throw new AccessDeniedException("delivery is assigned to another courier");
        }
        return DeliveryResponse.from(job);
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID suborderId, AuthPrincipal principal) {
        dispatch.accept(suborderId, courierId(principal));
    }

    @PostMapping("/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID suborderId, AuthPrincipal principal) {
        dispatch.reject(suborderId, courierId(principal));
    }

    @PostMapping("/pickup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pickUp(@PathVariable UUID suborderId, AuthPrincipal principal) {
        dispatch.pickUp(suborderId, courierId(principal));
    }

    @PostMapping("/in-transit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inTransit(@PathVariable UUID suborderId, AuthPrincipal principal) {
        dispatch.startTransit(suborderId, courierId(principal));
    }

    @PostMapping("/deliver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deliver(@PathVariable UUID suborderId, AuthPrincipal principal) {
        dispatch.deliver(suborderId, courierId(principal));
    }

    @PostMapping("/fail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(@PathVariable UUID suborderId, @Valid @RequestBody FailRequest request, AuthPrincipal principal) {
        dispatch.fail(suborderId, courierId(principal), request.reason());
    }

    private UUID courierId(AuthPrincipal principal) {
        return couriers.requireByUserId(Authz.require(principal).userId()).getId();
    }
}
