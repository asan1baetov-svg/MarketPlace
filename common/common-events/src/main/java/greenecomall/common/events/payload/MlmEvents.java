package greenecomall.common.events.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload-контракты топика {@code mlm}.
 */
public final class MlmEvents {

    public record MlmAccessActivated(String mlmUserId, Instant deadline, long requiredAmountMinor, String currency) {
    }

    public record MlmPurchaseCounted(
            String mlmUserId,
            UUID orderId,
            long achievedAmountMinor,
            long requiredAmountMinor,
            String currency) {
    }

    public record MlmConditionMet(String mlmUserId, long achievedAmountMinor, String currency, List<UUID> orderIds) {
    }

    public record MlmActivated(String mlmUserId, Instant activatedAt) {
    }

    public record MlmExpired(String mlmUserId, Instant deadline) {
    }

    public record MlmReferralBonusAccrued(
            UUID mlmAccountId,
            UUID fromAccountId,
            int level,
            long amountMinor,
            String currency) {
    }

    private MlmEvents() {
    }
}
