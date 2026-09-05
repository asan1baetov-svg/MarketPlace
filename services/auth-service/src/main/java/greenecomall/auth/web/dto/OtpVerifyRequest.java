package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(@NotBlank String target, @NotBlank String purpose, @NotBlank String code) {
}
