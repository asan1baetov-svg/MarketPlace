package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(String email, String phone, @NotBlank String password, String locale) {
}
