package greenecomall.order.web;

import greenecomall.common.domain.DomainException;
import greenecomall.order.OrderErrors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Уточняет HTTP-статус доменных ошибок order-service (общий {@code RestExceptionHandler}
 * из common-web всегда отдаёт 422). Тот же приём, что {@code CatalogExceptionHandler}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handle(DomainException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case OrderErrors.ORDER_NOT_FOUND,
                 OrderErrors.SUBORDER_NOT_FOUND,
                 OrderErrors.CART_ITEM_NOT_FOUND,
                 OrderErrors.PROMOCODE_NOT_FOUND,
                 OrderErrors.SHOP_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case OrderErrors.ORDER_STATUS_INVALID,
                 OrderErrors.SUBORDER_STATUS_INVALID,
                 OrderErrors.CART_CITY_MISMATCH,
                 OrderErrors.CART_EMPTY,
                 OrderErrors.CURRENCY_MISMATCH,
                 OrderErrors.STOCK_INSUFFICIENT,
                 OrderErrors.PRODUCT_UNAVAILABLE,
                 OrderErrors.PROMOCODE_CODE_TAKEN -> HttpStatus.CONFLICT;
            case OrderErrors.ORDER_FORBIDDEN,
                 OrderErrors.SUBORDER_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case OrderErrors.PROMOCODE_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
            case OrderErrors.CATALOG_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle("Order error");
        pd.setType(URI.create("urn:green-eco-mall:error:" + ex.getCode()));
        pd.setProperty("code", ex.getCode());
        return pd;
    }
}
