package greenecomall.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;
import greenecomall.common.web.security.ServiceSecurityConfiguration;

/**
 * Корзина, заказы, suborders, конечный автомат статусов, промокоды
 */
@SpringBootApplication
@Import({RestExceptionHandler.class, ServiceSecurityConfiguration.class})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
