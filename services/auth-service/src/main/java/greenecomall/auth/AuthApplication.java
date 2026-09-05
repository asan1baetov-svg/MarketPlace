package greenecomall.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;

/**
 * Аутентификация, роли (RBAC), JWT + refresh, OTP, SSO-вход из внешней MLM-системы
 */
@SpringBootApplication
@Import(RestExceptionHandler.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
