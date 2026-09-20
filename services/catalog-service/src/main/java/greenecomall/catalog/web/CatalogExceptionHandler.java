package greenecomall.catalog.web;

import greenecomall.catalog.CatalogErrors;
import greenecomall.common.domain.DomainException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Уточняет HTTP-статус для доменных ошибок catalog-service (общий {@code RestExceptionHandler}
 * из common-web всегда отдаёт 422). Тот же приём, что в {@code AuthExceptionHandler} auth-service.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handle(DomainException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case CatalogErrors.COUNTRY_NOT_FOUND,
                 CatalogErrors.CITY_NOT_FOUND,
                 CatalogErrors.ZONE_NOT_FOUND,
                 CatalogErrors.CATEGORY_NOT_FOUND,
                 CatalogErrors.SHOP_NOT_FOUND,
                 CatalogErrors.PRODUCT_NOT_FOUND,
                 CatalogErrors.STOCK_NOT_FOUND,
                 CatalogErrors.MARKUP_RULE_NOT_FOUND,
                 CatalogErrors.REVIEW_NOT_FOUND,
                 CatalogErrors.IMAGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CatalogErrors.COUNTRY_CODE_TAKEN,
                 CatalogErrors.GEO_HAS_DEPENDENTS,
                 CatalogErrors.CATEGORY_SLUG_TAKEN,
                 CatalogErrors.CATEGORY_HAS_DEPENDENTS,
                 CatalogErrors.SHOP_STATUS_INVALID,
                 CatalogErrors.PRODUCT_STATUS_INVALID,
                 CatalogErrors.STOCK_INSUFFICIENT -> HttpStatus.CONFLICT;
            case CatalogErrors.SHOP_FORBIDDEN,
                 CatalogErrors.PRODUCT_FORBIDDEN,
                 CatalogErrors.SHOP_NOT_ACTIVE,
                 CatalogErrors.FORBIDDEN,
                 CatalogErrors.REVIEW_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            case CatalogErrors.ORDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case CatalogErrors.MARKUP_RULE_INVALID,
                 CatalogErrors.IMAGE_INVALID -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle("Catalog error");
        pd.setType(URI.create("urn:green-eco-mall:error:" + ex.getCode()));
        pd.setProperty("code", ex.getCode());
        return pd;
    }
}
