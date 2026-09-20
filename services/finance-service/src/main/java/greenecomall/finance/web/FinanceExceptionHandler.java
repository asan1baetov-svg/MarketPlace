package greenecomall.finance.web;

import greenecomall.common.domain.DomainException;
import greenecomall.finance.FinanceErrors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Уточняет HTTP-статус доменных ошибок finance-service (тот же приём, что в catalog/order). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FinanceExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handle(DomainException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case FinanceErrors.PAYMENT_NOT_FOUND,
                 FinanceErrors.WALLET_NOT_FOUND,
                 FinanceErrors.PAYOUT_NOT_FOUND,
                 FinanceErrors.WEBHOOK_UNKNOWN_PAYMENT,
                 FinanceErrors.MLM_TARIFF_NOT_FOUND,
                 FinanceErrors.REQUISITE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FinanceErrors.MLM_ACCOUNT_REQUIRED -> HttpStatus.FORBIDDEN;
            case FinanceErrors.MLM_UNAVAILABLE,
                 FinanceErrors.ACQUIRING_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case FinanceErrors.PAYMENT_STATUS_INVALID,
                 FinanceErrors.PAYMENT_ALREADY_EXISTS,
                 FinanceErrors.PAYOUT_STATUS_INVALID,
                 FinanceErrors.REQUISITE_STATUS_INVALID,
                 FinanceErrors.WALLET_INSUFFICIENT_FUNDS,
                 FinanceErrors.CURRENCY_MISMATCH -> HttpStatus.CONFLICT;
            case FinanceErrors.WEBHOOK_SIGNATURE_INVALID -> HttpStatus.UNAUTHORIZED;
            case FinanceErrors.PAYMENT_AMOUNT_INVALID,
                 FinanceErrors.REQUISITE_INVALID -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle("Finance error");
        pd.setType(URI.create("urn:green-eco-mall:error:" + ex.getCode()));
        pd.setProperty("code", ex.getCode());
        return pd;
    }
}
