package greenecomall.courier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;
import greenecomall.common.web.security.ServiceSecurityConfiguration;

/**
 * Курьеры, подбор под suborder, конечный автомат доставки, заработок
 */
@SpringBootApplication
@Import({RestExceptionHandler.class, ServiceSecurityConfiguration.class})
public class CourierApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourierApplication.class, args);
    }
}
