package greenecomall.finance.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Сумма и валюта берутся из тарифа mlm-service, MLM-аккаунт и клиент — из access-JWT. */
public record CreateMlmAccessPaymentRequest(@NotNull UUID tariffId) {
}
