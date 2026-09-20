package greenecomall.mlm.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Антикоррупционный слой обратной синхронизации «маркетплейс → внешний MLM-бэк»
 * (docs/ARCHITECTURE.md §5). Остальной код mlm-service знает только эти записи, а не
 * протокол/эндпоинты внешней системы.
 */
public interface MlmSyncClient {

    /** @param idempotencyKey стабильный ключ сообщения (повтор не должен задваивать обработку на той стороне) */
    void reportActivationStatus(ActivationStatus status, String idempotencyKey);

    void reportPurchase(Purchase purchase, String idempotencyKey);

    /**
     * @param status {@code CONDITION_MET} (купил на X в срок — аккаунт активен) или {@code EXPIRED}
     */
    record ActivationStatus(String mlmUserId, String status, long achievedAmountMinor, long requiredAmountMinor,
                            String currency, Instant occurredAt, List<UUID> marketplaceOrderIds) {
    }

    /** @param status {@code PAID} или {@code REVERSED} (отмена/возврат ранее сообщённой покупки) */
    record Purchase(String mlmUserId, UUID orderId, long amountMinor, String currency, String status,
                    Instant occurredAt) {
    }
}
