package greenecomall.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;

/**
 * Уведомления: push/SMS/Telegram, шаблоны, настройки каналов
 */
@SpringBootApplication
@Import(RestExceptionHandler.class)
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
