package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalAddRoleRequest(@NotBlank String role) {
}
