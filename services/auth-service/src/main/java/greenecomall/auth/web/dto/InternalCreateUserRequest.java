package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code status} — необязательный, регистронезависимый код {@code UserStatus}; по умолчанию PENDING. */
public record InternalCreateUserRequest(String email, String phone, @NotBlank String role, String status) {
}
