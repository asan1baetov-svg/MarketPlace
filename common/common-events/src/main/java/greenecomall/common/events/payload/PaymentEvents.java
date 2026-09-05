package greenecomall.common.events.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload-контракты топика {@code payments}.
 */
public final class PaymentEvents {

    public record OrderPaid(
            UUID paymentId,
            UUID orderId,
            long amountMinor,
            String currency,
            Instant paidAt) {
    }

    public record PaymentFailed(UUID paymentId, UUID orderId, String reason) {
    }

    public record PaymentRefunded(UUID paymentId, UUID orderId, long amountMinor, String currency) {
    }

    public record MlmAccessPaid(
            UUID paymentId,
            String mlmUserId,
            UUID tariffId,
            long amountMinor,
            String currency,
            Instant paidAt) {
    }

    private PaymentEvents() {
    }
}
