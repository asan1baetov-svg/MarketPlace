package greenecomall.common.events.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload-контракты топика {@code couriers}.
 */
public final class CourierEvents {

    public record CourierAssigned(UUID suborderId, UUID courierId) {
    }

    public record CourierAccepted(UUID suborderId, UUID courierId) {
    }

    public record DeliveryStatusChanged(
            UUID suborderId,
            UUID courierId,
            String status,
            Instant at,
            String failureReason) {
    }

    public record NoCourierAvailable(UUID suborderId, UUID cityId) {
    }

    private CourierEvents() {
    }
}
