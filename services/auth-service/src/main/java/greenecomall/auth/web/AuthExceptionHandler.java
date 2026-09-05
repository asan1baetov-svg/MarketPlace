package greenecomall.auth.web;

import greenecomall.auth.AuthErrors;
import greenecomall.common.domain.DomainException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Уточняет HTTP-статус для доменных ошибок auth-service (общий {@code RestExceptionHandler} из
 * common-web всегда отдаёт 422). Контракт статусов — docs/TASK-01-auth-service.md §3.
 * {@code @Order(HIGHEST_PRECEDENCE)}, чтобы перекрыть общий обработчик для этого модуля.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handle(DomainException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case AuthErrors.CREDENTIALS_INVALID,
                 AuthErrors.REFRESH_INVALID,
                 AuthErrors.REFRESH_REUSED,
                 AuthErrors.SSO_TOKEN_INVALID,
                 AuthErrors.SSO_TOKEN_REPLAYED -> HttpStatus.UNAUTHORIZED;
            case AuthErrors.USER_BLOCKED, AuthErrors.USER_NOT_ACTIVE -> HttpStatus.FORBIDDEN;
            case AuthErrors.CONTACT_TAKEN -> HttpStatus.CONFLICT;
            case AuthErrors.OTP_ATTEMPTS_EXCEEDED, AuthErrors.OTP_TOO_FREQUENT -> HttpStatus.TOO_MANY_REQUESTS;
            case AuthErrors.OTP_INVALID, AuthErrors.OTP_EXPIRED, AuthErrors.OTP_PURPOSE_UNSUPPORTED ->
                    HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle("Auth error");
        pd.setType(URI.create("urn:green-eco-mall:error:" + ex.getCode()));
        pd.setProperty("code", ex.getCode());
        return pd;
    }
}
