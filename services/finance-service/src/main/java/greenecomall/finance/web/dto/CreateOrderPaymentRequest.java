package greenecomall.finance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateOrderPaymentRequest(
        @NotNull UUID orderId,
        @NotNull UUID clientUserId,
        @Positive long amountMinor,
        @NotBlank String currency) {
}
