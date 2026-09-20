package greenecomall.finance.web.dto;

import greenecomall.finance.domain.PayoutRequest;

import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
        UUID id, UUID walletId, long amountMinor, String currency, String status,
        String requestedBy, String approvedBy, Instant createdAt, Instant processedAt,
        boolean auto, int attempts, String lastError) {

    public static PayoutResponse from(PayoutRequest p) {
        return new PayoutResponse(p.getId(), p.getWalletId(), p.getAmountMinor(), p.getCurrency(),
                p.getStatus().name(), p.getRequestedBy(), p.getApprovedBy(), p.getCreatedAt(), p.getProcessedAt(),
                p.isAuto(), p.getAttempts(), p.getLastError());
    }
}
