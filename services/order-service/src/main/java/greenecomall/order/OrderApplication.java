package greenecomall.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;

/**
 * Корзина, заказы, suborders, конечный автомат статусов, промокоды
 */
@SpringBootApplication
@Import(RestExceptionHandler.class)
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
