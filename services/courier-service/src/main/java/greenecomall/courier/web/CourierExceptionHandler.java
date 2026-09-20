package greenecomall.courier.web;

import greenecomall.common.domain.DomainException;
import greenecomall.courier.CourierErrors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Уточняет HTTP-статус доменных ошибок courier-service. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CourierExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handle(DomainException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case CourierErrors.COURIER_NOT_FOUND, CourierErrors.JOB_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CourierErrors.COURIER_ALREADY_REGISTERED,
                 CourierErrors.MODERATION_STATUS_INVALID,
                 CourierErrors.JOB_STATUS_INVALID,
                 CourierErrors.JOB_NOT_READY_FOR_PICKUP -> HttpStatus.CONFLICT;
            case CourierErrors.COURIER_NOT_APPROVED, CourierErrors.JOB_FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle("Courier error");
        pd.setType(URI.create("urn:green-eco-mall:error:" + ex.getCode()));
        pd.setProperty("code", ex.getCode());
        return pd;
    }
}
