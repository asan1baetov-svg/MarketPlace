package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code login} — email или телефон. */
public record LoginRequest(@NotBlank String login, @NotBlank String password) {
}
