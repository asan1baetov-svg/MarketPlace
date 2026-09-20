package greenecomall.common.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Проверяет служебный токен на {@code /internal/**}: эти пути вызывают только другие сервисы
 * кластера, наружу через api-gateway они не публикуются. Сравнение — за постоянное время.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    private final byte[] expectedToken;

    public InternalTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken == null || expectedToken.isBlank()
                ? null : expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expectedToken == null || provided == null
                || !MessageDigest.isEqual(expectedToken, provided.getBytes(StandardCharsets.UTF_8))) {
            SecurityProblems.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "security.internal_token_invalid", "missing or invalid " + HEADER);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority(ROLE_INTERNAL))));
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
