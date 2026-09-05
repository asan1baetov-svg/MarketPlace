package greenecomall.common.events.payload;

import java.util.UUID;

/**
 * Payload-контракты топика {@code wallet}.
 */
public final class WalletEvents {

    public record WalletCredited(
            UUID walletId,
            String ownerType,
            String ownerRef,
            long amountMinor,
            String currency,
            String type,
            String referenceId) {
    }

    public record WalletDebited(
            UUID walletId,
            long amountMinor,
            String currency,
            String type,
            String referenceId) {
    }

    public record PayoutRequested(UUID payoutId, UUID walletId, long amountMinor, String currency) {
    }

    public record PayoutStatusChanged(UUID payoutId, long amountMinor, String currency, String status) {
    }

    private WalletEvents() {
    }
}
