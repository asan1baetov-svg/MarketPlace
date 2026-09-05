package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code purpose} — регистрозависимо не важно, парсится как {@code OtpPurpose} без учёта регистра. */
public record OtpRequest(@NotBlank String target, @NotBlank String purpose) {
}
