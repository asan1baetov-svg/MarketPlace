package greenecomall.auth.token;

import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Данные пользователя, попадающие в claims access-JWT.
 */
public record TokenSubject(UUID userId, Set<String> roles, String clientType, String mlmUserId) {

    public static TokenSubject of(User user, String mlmUserId) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toUnmodifiableSet());
        return new TokenSubject(user.getId(), roles, user.getClientType().name().toLowerCase(), mlmUserId);
    }
}
