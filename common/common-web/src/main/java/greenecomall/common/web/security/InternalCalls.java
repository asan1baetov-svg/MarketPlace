package greenecomall.common.web.security;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/** Исходящие служебные вызовы между сервисами: добавляет {@code X-Internal-Token}. */
public final class InternalCalls {

    private InternalCalls() {
    }

    public static ClientHttpRequestInterceptor internalToken(GemSecurityProperties props) {
        String token = props.internalToken();
        return (request, body, execution) -> {
            if (token != null && !token.isBlank()) {
                request.getHeaders().set(InternalTokenFilter.HEADER, token);
            }
            return execution.execute(request, body);
        };
    }
}
