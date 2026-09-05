package greenecomall.auth.web.dto;

import java.util.Set;
import java.util.UUID;

public record MeResponse(
        UUID userId, String email, String phone, Set<String> roles, String clientType, String mlmUserId) {
}
