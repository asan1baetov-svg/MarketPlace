package greenecomall.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;

/**
 * Магазины, товары, категории, гео-справочники, наценка, гео-фильтрованная витрина, кэш
 */
@SpringBootApplication
@Import(RestExceptionHandler.class)
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
