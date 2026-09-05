package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoRequest(@NotBlank String token) {
}
