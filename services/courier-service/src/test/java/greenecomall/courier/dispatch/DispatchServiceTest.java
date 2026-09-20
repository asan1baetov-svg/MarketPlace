package greenecomall.courier.dispatch;

import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.courier.config.CourierProperties;
import greenecomall.courier.domain.AssignmentOffer;
import greenecomall.courier.domain.Courier;
import greenecomall.courier.domain.DeliveryJob;
import greenecomall.courier.repo.AssignmentOfferRepository;
import greenecomall.courier.repo.CourierEarningRepository;
import greenecomall.courier.repo.CourierRepository;
import greenecomall.courier.repo.DeliveryJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock private DeliveryJobRepository jobs;
    @Mock private CourierRepository couriers;
    @Mock private AssignmentOfferRepository offers;
    @Mock private CourierEarningRepository earnings;
    @Mock private DomainEventPublisher events;

    private DispatchService service;
    private final UUID city = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DispatchService(jobs, couriers, offers, earnings, events,
                new CourierProperties(Duration.ofMinutes(2), 15_000, "KGS"), Clock.systemUTC());
    }

    private Courier activeCourier(String rating) {
        Courier c = new Courier(UUID.randomUUID(), UUID.randomUUID(), city, Set.of(), null);
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
        c.approve();
        c.changeStatus(Courier.Status.ACTIVE);
        ReflectionTestUtils.setField(c, "rating", new BigDecimal(rating));
        return c;
    }

    private DeliveryJob paidJob() {
        DeliveryJob job = new DeliveryJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), city);
        job.markPaid();
        return job;
    }

    @Test
    void offersToLeastLoadedCourierThenHighestRating() {
        Courier busy = activeCourier("5.0");
        Courier freeLow = activeCourier("3.0");
        Courier freeHigh = activeCourier("4.5");
        DeliveryJob job = paidJob();
        when(offers.findBySuborderId(job.getSuborderId())).thenReturn(List.of());
        when(couriers.findDispatchableInCity(city)).thenReturn(List.of(busy, freeLow, freeHigh));
        when(jobs.countActive(eq(busy.getId()), any())).thenReturn(2L);
        when(jobs.countActive(eq(freeLow.getId()), any())).thenReturn(0L);
        when(jobs.countActive(eq(freeHigh.getId()), any())).thenReturn(0L);

        service.dispatch(job);

        assertThat(job.getStatus()).isEqualTo(DeliveryJob.Status.OFFERED);
        assertThat(job.getCourierId()).isEqualTo(freeHigh.getId());
        verify(offers).save(any(AssignmentOffer.class));
        ArgumentCaptor<EventEnvelope<?>> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events).publish(eq("couriers"), anyString(), env.capture());
        assertThat(env.getValue().eventType()).isEqualTo(EventTypes.COURIER_ASSIGNED);
    }

    @Test
    void courierWhoRejectedIsSkipped() {
        Courier rejecter = activeCourier("5.0");
        Courier other = activeCourier("1.0");
        DeliveryJob job = paidJob();
        AssignmentOffer rejected = new AssignmentOffer(job.getSuborderId(), rejecter.getId(), java.time.Instant.now());
        rejected.close(AssignmentOffer.Status.REJECTED);
        when(offers.findBySuborderId(job.getSuborderId())).thenReturn(List.of(rejected));
        when(couriers.findDispatchableInCity(city)).thenReturn(List.of(rejecter, other));

        service.dispatch(job);

        assertThat(job.getCourierId()).isEqualTo(other.getId());
    }

    @Test
    void noCandidates_marksNoCourierAndNotifiesOnce() {
        DeliveryJob job = paidJob();
        when(offers.findBySuborderId(job.getSuborderId())).thenReturn(List.of());
        when(couriers.findDispatchableInCity(city)).thenReturn(List.of());

        service.dispatch(job);
        service.dispatch(job); // повторная попытка по расписанию

        assertThat(job.getStatus()).isEqualTo(DeliveryJob.Status.NO_COURIER);
        ArgumentCaptor<EventEnvelope<?>> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events).publish(eq("couriers"), anyString(), env.capture());
        assertThat(env.getValue().eventType()).isEqualTo(EventTypes.NO_COURIER_AVAILABLE);
    }
}
