package greenecomall.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Аутентифицированный пользователь в контексте запроса.
 *
 * @param userId     id пользователя в auth-service
 * @param roles      коды ролей, см. {@link Roles}
 * @param clientType {@code external} / {@code internal_mlm}; null для не-клиентских ролей
 * @param mlmUserId  внешний id в MLM-системе; null для не-MLM пользователей
 */
public record AuthPrincipal(UUID userId, Set<String> roles, String clientType, String mlmUserId) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
