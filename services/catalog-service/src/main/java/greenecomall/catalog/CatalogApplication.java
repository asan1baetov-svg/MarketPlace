package greenecomall.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;
import greenecomall.common.web.security.ServiceSecurityConfiguration;

/**
 * Магазины, товары, категории, гео-справочники, наценка, гео-фильтрованная витрина, кэш
 */
@SpringBootApplication
@Import({RestExceptionHandler.class, ServiceSecurityConfiguration.class})
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
