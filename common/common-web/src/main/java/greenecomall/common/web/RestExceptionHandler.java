package greenecomall.common.web;

import greenecomall.common.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Общий обработчик ошибок. Отдаёт {@link ProblemDetail} (RFC 7807);
 * доменный код ошибки кладётся в свойство {@code code} для i18n на клиенте.
 * Подключается в сервисе через {@code @Import(RestExceptionHandler.class)}.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Domain rule violated");
        pd.setType(URI.create("urn:green-eco-mall:error:" + ex.getCode()));
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    /** Отказ проверки владения ({@code Authz}) внутри контроллера/сервиса — иначе его перехватил бы общий 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Forbidden");
        pd.setType(URI.create("urn:green-eco-mall:error:security.forbidden"));
        pd.setProperty("code", "security.forbidden");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details);
        pd.setTitle("Validation failed");
        pd.setProperty("code", "validation.failed");
        return pd;
    }

    /** Нечитаемое тело (битый JSON, неизвестное значение enum) — ошибка клиента, а не 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "malformed request body");
        pd.setTitle("Bad request");
        pd.setProperty("code", "request.malformed");
        return pd;
    }

    /** Параметр пути/запроса не того типа (например, не UUID). */
    @ExceptionHandler(TypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(TypeMismatchException ex) {
        String name = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, name + ": invalid value");
        pd.setTitle("Bad request");
        pd.setProperty("code", "request.invalid_parameter");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse spring) {
            // Стандартные ошибки Spring MVC (404 нет маршрута, 405, 415, нет обязательного параметра) — свой статус
            ProblemDetail pd = spring.getBody();
            pd.setProperty("code", "http." + spring.getStatusCode().value());
            return pd;
        }
        log.error("unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        pd.setTitle("Internal error");
        pd.setProperty("code", "internal.error");
        return pd;
    }
}
