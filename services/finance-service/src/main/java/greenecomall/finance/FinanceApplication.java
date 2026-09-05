package greenecomall.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;

/**
 * Эквайринг и webhook, кошельки, ledger двойной записи, заявки на вывод
 */
@SpringBootApplication
@Import(RestExceptionHandler.class)
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
