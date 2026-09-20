package greenecomall.courier.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.PageResponse;
import greenecomall.common.web.security.Authz;
import greenecomall.courier.courier.CourierService;
import greenecomall.courier.domain.Courier;
import greenecomall.courier.domain.DeliveryJob;
import greenecomall.courier.repo.CourierEarningRepository;
import greenecomall.courier.repo.DeliveryJobRepository;
import greenecomall.courier.web.dto.CourierDtos.CourierResponse;
import greenecomall.courier.web.dto.CourierDtos.DeliveryResponse;
import greenecomall.courier.web.dto.CourierDtos.EarningsResponse;
import greenecomall.courier.web.dto.CourierDtos.IdResponse;
import greenecomall.courier.web.dto.CourierDtos.RegisterCourierRequest;
import greenecomall.courier.web.dto.CourierDtos.StatusRequest;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Эндпоинты курьера: заявка на регистрацию, выход на линию, свои доставки, заработок.
 * Курьер определяется по {@code sub} access-JWT ({@code /couriers/me/**}); админ смотрит любого
 * через {@code /admin/couriers}.
 */
@RestController
public class CourierController {

    private static final Set<DeliveryJob.Status> IN_WORK = EnumSet.of(
            DeliveryJob.Status.ACCEPTED, DeliveryJob.Status.PICKED_UP, DeliveryJob.Status.IN_TRANSIT);
    private static final Set<DeliveryJob.Status> DONE = EnumSet.of(
            DeliveryJob.Status.DELIVERED, DeliveryJob.Status.FAILED, DeliveryJob.Status.CANCELLED);

    private final CourierService courierService;
    private final DeliveryJobRepository jobs;
    private final CourierEarningRepository earnings;

    public CourierController(CourierService courierService, DeliveryJobRepository jobs,
                             CourierEarningRepository earnings) {
        this.courierService = courierService;
        this.jobs = jobs;
        this.earnings = earnings;
    }

    @PostMapping("/couriers")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse register(@Valid @RequestBody RegisterCourierRequest request, AuthPrincipal principal) {
        return new IdResponse(courierService.register(Authz.require(principal).userId(), request.countryId(), request.cityId(),
                request.zoneIds(), request.vehicle() == null ? null : request.vehicle().toString()));
    }

    @GetMapping("/couriers/me")
    public CourierResponse me(AuthPrincipal principal) {
        return CourierResponse.from(currentCourier(principal));
    }

    @GetMapping("/couriers/{id}")
    public CourierResponse get(@PathVariable UUID id, AuthPrincipal principal) {
        Courier courier = courierService.get(id);
        Authz.requireSelfOrAdmin(principal, courier.getUserId());
        return CourierResponse.from(courier);
    }

    @PostMapping("/couriers/me/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(@Valid @RequestBody StatusRequest request, AuthPrincipal principal) {
        courierService.changeStatus(currentCourier(principal).getId(), request.status());
    }

    /** {@code bucket}: {@code new} — офферы, {@code active} — в работе, {@code done} — завершённые; без параметра — все. */
    @GetMapping("/couriers/me/assignments")
    public PageResponse<DeliveryResponse> assignments(@RequestParam(required = false) String bucket,
                                                      Pageable pageable, AuthPrincipal principal) {
        UUID id = currentCourier(principal).getId();
        Page<DeliveryJob> page = switch (bucket == null ? "" : bucket) {
            case "new" -> jobs.findByCourierIdAndStatusInOrderByCreatedAtDesc(id, EnumSet.of(DeliveryJob.Status.OFFERED), pageable);
            case "active" -> jobs.findByCourierIdAndStatusInOrderByCreatedAtDesc(id, IN_WORK, pageable);
            case "done" -> jobs.findByCourierIdAndStatusInOrderByCreatedAtDesc(id, DONE, pageable);
            default -> jobs.findByCourierIdOrderByCreatedAtDesc(id, pageable);
        };
        return PageResponse.of(page.getContent().stream().map(DeliveryResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @GetMapping("/couriers/me/earnings")
    public EarningsResponse earnings(@RequestParam(required = false) Instant from,
                                     @RequestParam(required = false) Instant to,
                                     AuthPrincipal principal) {
        UUID id = currentCourier(principal).getId();
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(30, ChronoUnit.DAYS);
        return new EarningsResponse(id, start, end, earnings.sumForPeriod(id, start, end),
                earnings.findByCourierIdAndCreatedAtBetweenOrderByCreatedAtDesc(id, start, end).stream()
                        .map(EarningsResponse.Item::from).toList());
    }

    private Courier currentCourier(AuthPrincipal principal) {
        return courierService.requireByUserId(Authz.require(principal).userId());
    }
}
