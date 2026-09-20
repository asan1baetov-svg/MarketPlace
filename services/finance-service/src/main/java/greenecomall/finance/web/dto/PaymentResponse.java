package greenecomall.finance.web.dto;

import greenecomall.finance.domain.Payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id, String type, String status, UUID orderId, String mlmUserId,
        long amountMinor, String currency, String provider, String providerPaymentId,
        String redirectUrl, Instant createdAt) {

    public static PaymentResponse from(Payment p, String redirectUrl) {
        return new PaymentResponse(
                p.getId(), p.getType().name(), p.getStatus().name(), p.getOrderId(), p.getMlmUserId(),
                p.getAmountMinor(), p.getCurrency(), p.getProvider(), p.getProviderPaymentId(),
                redirectUrl, p.getCreatedAt());
    }
}
