package greenecomall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Единая точка входа для web/mobile/bot: маршрутизация на сервисы, валидация JWT,
 * проброс identity вниз через заголовки {@code X-User-*}, rate limiting, агрегация для админки.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
