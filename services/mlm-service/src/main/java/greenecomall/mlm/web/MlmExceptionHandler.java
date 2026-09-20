package greenecomall.mlm.web;

import greenecomall.common.domain.DomainException;
import greenecomall.mlm.MlmErrors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Уточняет HTTP-статус доменных ошибок mlm-service. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MlmExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handle(DomainException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case MlmErrors.ACCOUNT_NOT_FOUND, MlmErrors.TARIFF_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case MlmErrors.ACCESS_STATUS_INVALID, MlmErrors.NO_DEFAULT_TARIFF, MlmErrors.TARIFF_CONFLICT -> HttpStatus.CONFLICT;
            case MlmErrors.ACCOUNT_BLOCKED -> HttpStatus.FORBIDDEN;
            case MlmErrors.WEBHOOK_SIGNATURE_INVALID -> HttpStatus.UNAUTHORIZED;
            case MlmErrors.WEBHOOK_BODY_INVALID -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle("MLM error");
        pd.setType(URI.create("urn:green-eco-mall:error:" + ex.getCode()));
        pd.setProperty("code", ex.getCode());
        return pd;
    }
}
