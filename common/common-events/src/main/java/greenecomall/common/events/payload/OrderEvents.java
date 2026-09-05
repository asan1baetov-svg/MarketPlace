package greenecomall.common.events.payload;

import java.util.List;
import java.util.UUID;

/**
 * Payload-контракты топика {@code orders}.
 */
public final class OrderEvents {

    public record SuborderLine(UUID suborderId, UUID shopId, long goodsAmountMinor, long costAmountMinor) {
    }

    public record OrderCreated(
            UUID orderId,
            UUID clientUserId,
            UUID cityId,
            List<SuborderLine> suborders,
            long totalAmountMinor,
            String currency) {
    }

    public record OrderCancelled(UUID orderId, String reason, List<UUID> suborderIds) {
    }

    public record SuborderStatusChanged(
            UUID suborderId,
            UUID orderId,
            UUID shopId,
            String fromStatus,
            String toStatus) {
    }

    public record OrderCompleted(UUID orderId, UUID clientUserId, long totalAmountMinor, String currency) {
    }

    private OrderEvents() {
    }
}
