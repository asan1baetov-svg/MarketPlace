package greenecomall.finance.web.dto;

import greenecomall.finance.domain.WalletTransaction;

import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID id, String direction, long amountMinor, String currency, String type,
        String referenceType, String referenceId, long balanceAfterMinor, Instant createdAt) {

    public static WalletTransactionResponse from(WalletTransaction t) {
        return new WalletTransactionResponse(
                t.getId(), t.getDirection().name(), t.getAmountMinor(), t.getCurrency(), t.getType().name(),
                t.getReferenceType(), t.getReferenceId(), t.getBalanceAfterMinor(), t.getCreatedAt());
    }
}
