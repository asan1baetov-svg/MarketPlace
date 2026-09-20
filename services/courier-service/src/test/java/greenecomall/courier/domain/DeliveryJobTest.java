package greenecomall.courier.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.courier.CourierErrors;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryJobTest {

    private final UUID courier = UUID.randomUUID();

    private DeliveryJob paidJob() {
        DeliveryJob job = new DeliveryJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        job.markPaid();
        return job;
    }

    @Test
    void fullHappyPath() {
        DeliveryJob job = paidJob();
        job.offerTo(courier, Instant.now().plusSeconds(60));
        job.accept(courier, Instant.now());
        job.markReadyForPickup();
        job.pickUp(courier);
        job.startTransit(courier);
        job.deliver(courier, Instant.now());
        assertThat(job.getStatus()).isEqualTo(DeliveryJob.Status.DELIVERED);
        assertThat(job.getDeliveredAt()).isNotNull();
    }

    @Test
    void unpaidJobCannotBeOffered() {
        DeliveryJob job = new DeliveryJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> job.offerTo(courier, Instant.now()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(CourierErrors.JOB_STATUS_INVALID);
    }

    @Test
    void pickupRequiresShopToHaveAssembled() {
        DeliveryJob job = paidJob();
        job.offerTo(courier, Instant.now().plusSeconds(60));
        job.accept(courier, Instant.now());
        assertThatThrownBy(() -> job.pickUp(courier))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(CourierErrors.JOB_NOT_READY_FOR_PICKUP);
    }

    @Test
    void anotherCourierCannotAct() {
        DeliveryJob job = paidJob();
        job.offerTo(courier, Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> job.accept(UUID.randomUUID(), Instant.now()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(CourierErrors.JOB_FORBIDDEN);
    }

    @Test
    void rejectedOfferReturnsToQueue() {
        DeliveryJob job = paidJob();
        job.offerTo(courier, Instant.now().plusSeconds(60));
        job.returnToQueue();
        assertThat(job.getStatus()).isEqualTo(DeliveryJob.Status.AWAITING_COURIER);
        assertThat(job.getCourierId()).isNull();
    }
}
