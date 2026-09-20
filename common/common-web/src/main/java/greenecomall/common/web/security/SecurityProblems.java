package greenecomall.common.web.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Ответы 401/403 из security-фильтров в том же формате ProblemDetail, что и остальные ошибки. */
final class SecurityProblems {

    private SecurityProblems() {
    }

    static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> write(response, HttpServletResponse.SC_UNAUTHORIZED,
                "security.unauthorized", "authentication required");
    }

    static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> write(response, HttpServletResponse.SC_FORBIDDEN,
                "security.forbidden", "access denied");
    }

    static void write(HttpServletResponse response, int status, String code, String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        String title = status == HttpServletResponse.SC_UNAUTHORIZED ? "Unauthorized" : "Forbidden";
        response.getWriter().write("{\"type\":\"urn:green-eco-mall:error:" + code + "\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }
}
