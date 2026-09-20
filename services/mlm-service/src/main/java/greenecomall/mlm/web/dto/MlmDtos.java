package greenecomall.mlm.web.dto;

import greenecomall.mlm.domain.MlmAccount;
import greenecomall.mlm.domain.MlmBonusTransaction;
import greenecomall.mlm.domain.MlmOrder;
import greenecomall.mlm.domain.MlmSyncMessage;
import greenecomall.mlm.domain.MlmTariff;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Запросы/ответы mlm-service. */
public final class MlmDtos {

    private MlmDtos() {
    }

    /** Личный кабинет: статус активации, таймер до дедлайна, прогресс, бонусный баланс. */
    public record AccountResponse(
            UUID id, UUID userId, String mlmUserId, String referralCode, String uplineMlmUserId,
            String accessStatus, String externalStatus, boolean blocked,
            Instant accessPaidAt, Instant activationDeadline, Long secondsToDeadline,
            long requiredPurchaseAmountMinor, long achievedPurchaseAmountMinor, long remainingMinor,
            Instant activatedAt, long bonusBalanceMinor, String currency) {

        public static AccountResponse from(MlmAccount a, Instant now) {
            Long seconds = a.getActivationDeadline() == null ? null
                    : Math.max(Duration.between(now, a.getActivationDeadline()).getSeconds(), 0L);
            return new AccountResponse(a.getId(), a.getUserId(), a.getMlmUserId(), a.getReferralCode(),
                    a.getUplineMlmUserId(), a.getAccessStatus().name(), a.getExternalStatus(), a.isBlocked(),
                    a.getAccessPaidAt(), a.getActivationDeadline(), seconds,
                    a.getRequiredPurchaseAmountMinor(), a.getAchievedPurchaseAmountMinor(), a.remainingMinor(),
                    a.getActivatedAt(), a.getBonusBalanceMinor(), a.getCurrency());
        }
    }

    public record OrderResponse(UUID orderId, long amountMinor, String currency, String status, Instant paidAt) {
        public static OrderResponse from(MlmOrder o) {
            return new OrderResponse(o.getOrderId(), o.getAmountMinor(), o.getCurrency(), o.getStatus().name(), o.getPaidAt());
        }
    }

    public record BonusResponse(UUID id, String sourceType, String sourceRef, UUID fromAccountId, int level,
                                BigDecimal percent, long amountMinor, String currency, String status, Instant createdAt) {
        public static BonusResponse from(MlmBonusTransaction t) {
            return new BonusResponse(t.getId(), t.getSourceType(), t.getSourceRef(), t.getFromAccountId(), t.getLevel(),
                    t.getPercent(), t.getAmountMinor(), t.getCurrency(), t.getStatus().name(), t.getCreatedAt());
        }
    }

    public record ReferralResponse(int level, String mlmUserId, String accessStatus, Instant joinedAt) {
    }

    public record TariffRequest(
            @NotBlank String name,
            UUID countryId,
            @PositiveOrZero long accessPriceMinor,
            @NotBlank String currency,
            @Positive long requiredPurchaseAmountMinor,
            @Positive int purchaseWindowDays,
            boolean isDefault,
            boolean active) {
    }

    public record TariffResponse(UUID id, String name, UUID countryId, long accessPriceMinor, String currency,
                                 long requiredPurchaseAmountMinor, int purchaseWindowDays, boolean isDefault,
                                 boolean active) {
        public static TariffResponse from(MlmTariff t) {
            return new TariffResponse(t.getId(), t.getName(), t.getCountryId(), t.getAccessPriceMinor(),
                    t.getCurrency(), t.getRequiredPurchaseAmountMinor(), t.getPurchaseWindowDays(), t.isDefault(),
                    t.isActive());
        }
    }

    public record RateRequest(@Positive int level, @NotNull BigDecimal percent) {
    }

    public record RateResponse(int level, BigDecimal percent) {
    }

    public record SummaryResponse(long total, long none, long mustPurchase, long active, long expired,
                                  long mlmRevenueMinor) {
    }

    public record SyncMessageResponse(UUID id, String type, String status, int attempts, Instant nextAttemptAt,
                                      String lastError, Instant createdAt) {
        public static SyncMessageResponse from(MlmSyncMessage m) {
            return new SyncMessageResponse(m.getId(), m.getType().name(), m.getStatus().name(), m.getAttempts(),
                    m.getNextAttemptAt(), m.getLastError(), m.getCreatedAt());
        }
    }

    /** Тело входящего webhook от внешнего MLM-бэка при изменении статуса аккаунта / оплате взноса. */
    public record AccountStatusWebhook(@NotBlank String mlmUserId, @NotBlank String status, UUID tariffId) {
    }
}
