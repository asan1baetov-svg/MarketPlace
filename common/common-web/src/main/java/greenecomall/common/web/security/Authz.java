package greenecomall.common.web.security;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.security.Roles;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

/**
 * Проверки владения ресурсом в контроллерах/сервисах. Роли на уровне путей задаются в
 * {@link ServiceAuthorizationRules}; здесь — «это мой заказ/кошелёк/магазин или я админ».
 * Отказ — {@link AccessDeniedException} → 403 (см. {@code RestExceptionHandler}).
 */
public final class Authz {

    private Authz() {
    }

    public static AuthPrincipal require(AuthPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("authentication required");
        }
        return principal;
    }

    public static boolean isAdmin(AuthPrincipal principal) {
        return principal != null && (principal.hasRole(Roles.ADMIN) || principal.hasRole(Roles.SUPER_ADMIN));
    }

    /** Разрешено владельцу ресурса и администраторам. */
    public static void requireSelfOrAdmin(AuthPrincipal principal, UUID ownerUserId) {
        require(principal);
        if (!isAdmin(principal) && (ownerUserId == null || !ownerUserId.equals(principal.userId()))) {
            throw new AccessDeniedException("resource belongs to another user");
        }
    }

    public static void requireAnyRole(AuthPrincipal principal, String... roles) {
        require(principal);
        for (String role : roles) {
            if (principal.hasRole(role)) {
                return;
            }
        }
        throw new AccessDeniedException("required role: " + String.join(" or ", roles));
    }
}
