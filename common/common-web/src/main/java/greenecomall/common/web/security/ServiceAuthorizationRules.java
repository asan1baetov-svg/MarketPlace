package greenecomall.common.web.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Правила доступа конкретного сервиса поверх общих (см. {@link ServiceSecurityConfiguration}):
 * публичные пути, требования ролей. Общая часть уже добавила: actuator — открыт,
 * {@code /admin/**} — только ADMIN/SUPER_ADMIN; после правил сервиса — {@code anyRequest().authenticated()}.
 */
@FunctionalInterface
public interface ServiceAuthorizationRules {

    void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules);
}
