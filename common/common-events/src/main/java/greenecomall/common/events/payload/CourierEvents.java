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

    /**
     * @param feeMinor вознаграждение курьеру за доставку (только для {@code DeliveryCompleted}, иначе null) —
     *                 finance-service зачисляет его на кошелёк курьера
     */
    public record DeliveryStatusChanged(
            UUID suborderId,
            UUID courierId,
            String status,
            Instant at,
            String failureReason,
            Long feeMinor,
            String currency) {
    }

    public record CourierRegistered(UUID courierId, UUID userId, UUID cityId) {
    }

    public record NoCourierAvailable(UUID suborderId, UUID cityId) {
    }

    private CourierEvents() {
    }
}
