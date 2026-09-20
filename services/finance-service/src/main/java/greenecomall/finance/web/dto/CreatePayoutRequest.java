package greenecomall.finance.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/** Автор заявки — пользователь из access-JWT; кошелёк должен ему принадлежать. */
public record CreatePayoutRequest(
        @NotNull UUID walletId,
        @Positive long amountMinor,
        @NotNull JsonNode bankDetails) {
}
