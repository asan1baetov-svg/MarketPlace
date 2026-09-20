package greenecomall.courier.courier;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.CourierEvents;
import greenecomall.courier.CourierErrors;
import greenecomall.courier.domain.Courier;
import greenecomall.courier.repo.CourierRepository;
import greenecomall.courier.support.Tracing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** Регистрация курьеров, модерация админом, выход на линию. */
@Service
public class CourierService {

    private static final String PRODUCER = "courier-service";

    private final CourierRepository couriers;
    private final DomainEventPublisher events;

    public CourierService(CourierRepository couriers, DomainEventPublisher events) {
        this.couriers = couriers;
        this.events = events;
    }

    @Transactional
    public UUID register(UUID userId, UUID countryId, UUID cityId, Set<UUID> zoneIds, String vehicleJson) {
        if (couriers.existsByUserId(userId)) {
            throw new DomainException(CourierErrors.COURIER_ALREADY_REGISTERED, "user " + userId + " is already a courier");
        }
        Courier courier = couriers.save(new Courier(userId, countryId, cityId, zoneIds, vehicleJson));
        events.publish(Topics.COURIERS, courier.getId().toString(),
                EventEnvelope.of(EventTypes.COURIER_REGISTERED, PRODUCER, Tracing.currentTraceId(),
                        new CourierEvents.CourierRegistered(courier.getId(), userId, cityId)));
        return courier.getId();
    }

    @Transactional(readOnly = true)
    public Courier get(UUID id) {
        return couriers.findById(id)
                .orElseThrow(() -> new DomainException(CourierErrors.COURIER_NOT_FOUND, "courier not found: " + id));
    }

    /** Курьер пользователя из access-JWT (один пользователь — один курьер). */
    @Transactional(readOnly = true)
    public Courier requireByUserId(UUID userId) {
        return couriers.findByUserId(userId)
                .orElseThrow(() -> new DomainException(CourierErrors.COURIER_NOT_FOUND, "user " + userId + " is not a courier"));
    }

    @Transactional(readOnly = true)
    public Page<Courier> search(UUID cityId, Courier.Moderation moderation, Pageable pageable) {
        return couriers.search(cityId, moderation, pageable);
    }

    /**
     * TODO(auth): после одобрения выдать пользователю роль COURIER через auth-service
     * ({@code POST /internal/auth/users/{id}/roles}) — отдельного события для этого в каталоге нет.
     */
    @Transactional
    public void approve(UUID id) {
        get(id).approve();
    }

    @Transactional
    public void reject(UUID id, String reason) {
        get(id).reject(reason);
    }

    @Transactional
    public void changeStatus(UUID id, Courier.Status status) {
        get(id).changeStatus(status);
    }
}
