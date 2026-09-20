package greenecomall.common.web.security;

import greenecomall.common.security.AuthPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Подставляет в параметр контроллера {@link AuthPrincipal} из <b>проверенного</b> access-JWT
 * (claims {@code sub}, {@code roles}, {@code client_type}, {@code mlm_user_id}).
 * Заголовки {@code X-User-*} намеренно не читаются: их может подделать любой, кто достучался
 * до сервиса мимо гейтвея. На публичных путях без токена подставляется {@code null}.
 */
public class AuthPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return current();
    }

    /** Текущий пользователь запроса или {@code null}, если запрос не аутентифицирован JWT. */
    public static AuthPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            return null;
        }
        Jwt jwt = token.getToken();
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new AuthPrincipal(
                UUID.fromString(jwt.getSubject()),
                roles == null ? Set.of() : Set.copyOf(roles),
                jwt.getClaimAsString("client_type"),
                jwt.getClaimAsString("mlm_user_id"));
    }
}
