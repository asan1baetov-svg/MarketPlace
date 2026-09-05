package greenecomall.common.events.payload;

import java.util.UUID;

/**
 * Payload-контракты топика {@code catalog}.
 */
public final class CatalogEvents {

    public record ShopApproved(UUID shopId, UUID ownerUserId, UUID cityId) {
    }

    public record ShopSuspended(UUID shopId, String reason) {
    }

    public record ProductPublished(
            UUID productId,
            UUID shopId,
            UUID cityId,
            UUID categoryId,
            long salePriceMinor,
            String currency) {
    }

    public record ProductRejected(UUID productId, UUID shopId, String reason) {
    }

    public record StockChanged(UUID productId, int quantity, int reserved) {
    }

    private CatalogEvents() {
    }
}
