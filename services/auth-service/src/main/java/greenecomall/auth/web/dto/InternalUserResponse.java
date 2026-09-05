package greenecomall.auth.web.dto;

import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record InternalUserResponse(UUID userId, Set<String> roles, String status, String clientType) {

    public static InternalUserResponse from(User user) {
        Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
        return new InternalUserResponse(
                user.getId(), roles, user.getStatus().name(), user.getClientType().name().toLowerCase());
    }
}
