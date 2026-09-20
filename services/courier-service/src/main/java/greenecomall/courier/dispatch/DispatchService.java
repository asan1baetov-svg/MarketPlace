package greenecomall.courier.dispatch;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.CourierEvents;
import greenecomall.common.events.payload.OrderEvents;
import greenecomall.courier.CourierErrors;
import greenecomall.courier.config.CourierProperties;
import greenecomall.courier.domain.AssignmentOffer;
import greenecomall.courier.domain.Courier;
import greenecomall.courier.domain.CourierEarning;
import greenecomall.courier.domain.DeliveryJob;
import greenecomall.courier.repo.AssignmentOfferRepository;
import greenecomall.courier.repo.CourierEarningRepository;
import greenecomall.courier.repo.CourierRepository;
import greenecomall.courier.repo.DeliveryJobRepository;
import greenecomall.courier.support.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Подбор курьера и конечный автомат доставки (docs/ARCHITECTURE.md §2.5, §3.4 ТЗ).
 * Кандидаты — одобренные курьеры на линии в городе магазина, которым этот suborder ещё не
 * предлагали; балансировка — минимальная текущая загрузка, затем рейтинг. Оффер живёт
 * {@code courier.offer-ttl}; отказ/истечение → следующий кандидат; кандидатов нет →
 * {@code NoCourierAvailable} (уведомление админу) и повторная попытка по расписанию.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);
    private static final String PRODUCER = "courier-service";
    private static final Set<DeliveryJob.Status> ACTIVE_LOAD =
            EnumSet.of(DeliveryJob.Status.OFFERED, DeliveryJob.Status.ACCEPTED,
                    DeliveryJob.Status.PICKED_UP, DeliveryJob.Status.IN_TRANSIT);

    private final DeliveryJobRepository jobs;
    private final CourierRepository couriers;
    private final AssignmentOfferRepository offers;
    private final CourierEarningRepository earnings;
    private final DomainEventPublisher events;
    private final CourierProperties properties;
    private final Clock clock;

    public DispatchService(DeliveryJobRepository jobs, CourierRepository couriers, AssignmentOfferRepository offers,
                           CourierEarningRepository earnings, DomainEventPublisher events,
                           CourierProperties properties, Clock clock) {
        this.jobs = jobs;
        this.couriers = couriers;
        this.offers = offers;
        this.earnings = earnings;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    // ─── реакция на события заказа/оплаты ──────────────────────────────────────

    @Transactional
    public void registerJobs(OrderEvents.OrderCreated event) {
        for (OrderEvents.SuborderLine line : event.suborders()) {
            if (!jobs.existsById(line.suborderId())) {
                jobs.save(new DeliveryJob(line.suborderId(), event.orderId(), line.shopId(), event.cityId()));
            }
        }
    }

    @Transactional
    public void onOrderPaid(UUID orderId) {
        for (DeliveryJob job : jobs.findByOrderId(orderId)) {
            if (job.getStatus() == DeliveryJob.Status.CREATED) {
                job.markPaid();
                dispatch(job);
            }
        }
    }

    @Transactional
    public void onSuborderAssembled(UUID suborderId) {
        jobs.findById(suborderId).ifPresent(DeliveryJob::markReadyForPickup);
    }

    @Transactional
    public void onOrderCancelled(UUID orderId) {
        for (DeliveryJob job : jobs.findByOrderId(orderId)) {
            if (job.getStatus() == DeliveryJob.Status.OFFERED) {
                closePendingOffer(job, AssignmentOffer.Status.EXPIRED);
            }
            job.cancel();
        }
    }

    // ─── подбор ────────────────────────────────────────────────────────────────

    /** Предлагает задачу лучшему свободному кандидату или помечает NO_COURIER. */
    void dispatch(DeliveryJob job) {
        // явно отказавшимся не предлагаем снова; просто пропустившим (EXPIRED) — можно
        Set<UUID> alreadyOffered = offers.findBySuborderId(job.getSuborderId()).stream()
                .filter(o -> o.getStatus() == AssignmentOffer.Status.REJECTED)
                .map(AssignmentOffer::getCourierId)
                .collect(Collectors.toSet());

        Optional<Courier> candidate = couriers.findDispatchableInCity(job.getCityId()).stream()
                .filter(c -> !alreadyOffered.contains(c.getId()))
                .min(Comparator.<Courier>comparingLong(c -> jobs.countActive(c.getId(), ACTIVE_LOAD))
                        .thenComparing(Courier::getRating, Comparator.reverseOrder()));

        if (candidate.isEmpty()) {
            boolean firstTime = job.getStatus() != DeliveryJob.Status.NO_COURIER;
            job.markNoCourier();
            if (firstTime) {
                publish(job, EventTypes.NO_COURIER_AVAILABLE,
                        new CourierEvents.NoCourierAvailable(job.getSuborderId(), job.getCityId()));
            }
            log.info("no courier available for suborder {} in city {}", job.getSuborderId(), job.getCityId());
            return;
        }

        Courier courier = candidate.get();
        Instant expiresAt = Instant.now(clock).plus(properties.offerTtl());
        job.offerTo(courier.getId(), expiresAt);
        offers.save(new AssignmentOffer(job.getSuborderId(), courier.getId(), expiresAt));
        publish(job, EventTypes.COURIER_ASSIGNED, new CourierEvents.CourierAssigned(job.getSuborderId(), courier.getId()));
    }

    /** Истёкшие офферы → следующий кандидат; задачи без курьера → повторная попытка. */
    @Transactional
    public int redispatchStale() {
        Instant now = Instant.now(clock);
        int touched = 0;
        for (DeliveryJob job : jobs.findByStatusAndOfferExpiresAtBefore(DeliveryJob.Status.OFFERED, now)) {
            closePendingOffer(job, AssignmentOffer.Status.EXPIRED);
            job.returnToQueue();
            dispatch(job);
            touched++;
        }
        for (DeliveryJob job : jobs.findByStatusIn(EnumSet.of(DeliveryJob.Status.AWAITING_COURIER, DeliveryJob.Status.NO_COURIER))) {
            dispatch(job);
            touched++;
        }
        return touched;
    }

    // ─── действия курьера ──────────────────────────────────────────────────────

    @Transactional
    public void accept(UUID suborderId, UUID courierId) {
        DeliveryJob job = requireJob(suborderId);
        job.accept(courierId, Instant.now(clock));
        closePendingOffer(job, AssignmentOffer.Status.ACCEPTED);
        publish(job, EventTypes.COURIER_ACCEPTED, new CourierEvents.CourierAccepted(suborderId, courierId));
    }

    @Transactional
    public void reject(UUID suborderId, UUID courierId) {
        DeliveryJob job = requireJob(suborderId);
        if (job.getStatus() != DeliveryJob.Status.OFFERED || !courierId.equals(job.getCourierId())) {
            throw new DomainException(CourierErrors.JOB_FORBIDDEN,
                    "suborder " + suborderId + " has no pending offer for courier " + courierId);
        }
        closePendingOffer(job, AssignmentOffer.Status.REJECTED);
        job.returnToQueue();
        dispatch(job);
    }

    @Transactional
    public void pickUp(UUID suborderId, UUID courierId) {
        DeliveryJob job = requireJob(suborderId);
        job.pickUp(courierId);
        publishDelivery(job, EventTypes.DELIVERY_PICKED_UP, null, null);
    }

    @Transactional
    public void startTransit(UUID suborderId, UUID courierId) {
        DeliveryJob job = requireJob(suborderId);
        job.startTransit(courierId);
        publishDelivery(job, EventTypes.DELIVERY_IN_TRANSIT, null, null);
    }

    @Transactional
    public void deliver(UUID suborderId, UUID courierId) {
        DeliveryJob job = requireJob(suborderId);
        job.deliver(courierId, Instant.now(clock));
        long fee = properties.deliveryFeeMinor();
        if (fee > 0 && !earnings.existsBySuborderId(suborderId)) {
            earnings.save(new CourierEarning(courierId, suborderId, fee, properties.currency()));
        }
        publishDelivery(job, EventTypes.DELIVERY_COMPLETED, null, fee > 0 ? fee : null);
    }

    @Transactional
    public void fail(UUID suborderId, UUID courierId, String reason) {
        DeliveryJob job = requireJob(suborderId);
        job.fail(courierId, reason);
        publishDelivery(job, EventTypes.DELIVERY_FAILED, reason, null);
    }

    // ─── админ ────────────────────────────────────────────────────────────────

    @Transactional
    public void assignManually(UUID suborderId, UUID courierId) {
        DeliveryJob job = requireJob(suborderId);
        Courier courier = couriers.findById(courierId)
                .orElseThrow(() -> new DomainException(CourierErrors.COURIER_NOT_FOUND, "courier not found: " + courierId));
        if (courier.getModerationStatus() != Courier.Moderation.APPROVED) {
            throw new DomainException(CourierErrors.COURIER_NOT_APPROVED, "courier " + courierId + " is not approved");
        }
        if (job.getStatus() == DeliveryJob.Status.OFFERED) {
            closePendingOffer(job, AssignmentOffer.Status.EXPIRED);
        }
        job.assignManually(courierId, Instant.now(clock));
        publish(job, EventTypes.COURIER_ASSIGNED, new CourierEvents.CourierAssigned(suborderId, courierId));
        publish(job, EventTypes.COURIER_ACCEPTED, new CourierEvents.CourierAccepted(suborderId, courierId));
    }

    @Transactional(readOnly = true)
    public DeliveryJob get(UUID suborderId) {
        return requireJob(suborderId);
    }

    @Transactional(readOnly = true)
    public List<Courier> available(UUID cityId) {
        return couriers.findDispatchableInCity(cityId);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void closePendingOffer(DeliveryJob job, AssignmentOffer.Status status) {
        if (job.getCourierId() != null) {
            offers.findFirstBySuborderIdAndCourierIdAndStatus(job.getSuborderId(), job.getCourierId(),
                    AssignmentOffer.Status.PENDING).ifPresent(o -> o.close(status));
        }
    }

    private DeliveryJob requireJob(UUID suborderId) {
        return jobs.findById(suborderId)
                .orElseThrow(() -> new DomainException(CourierErrors.JOB_NOT_FOUND, "no delivery for suborder " + suborderId));
    }

    private void publishDelivery(DeliveryJob job, String eventType, String failureReason, Long feeMinor) {
        publish(job, eventType, new CourierEvents.DeliveryStatusChanged(
                job.getSuborderId(), job.getCourierId(), job.getStatus().name(), Instant.now(clock),
                failureReason, feeMinor, feeMinor == null ? null : properties.currency()));
    }

    private void publish(DeliveryJob job, String eventType, Object payload) {
        events.publish(Topics.COURIERS, job.getOrderId().toString(),
                EventEnvelope.of(eventType, PRODUCER, Tracing.currentTraceId(), payload));
    }
}
