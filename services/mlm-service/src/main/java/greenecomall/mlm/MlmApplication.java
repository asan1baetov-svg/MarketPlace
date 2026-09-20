package greenecomall.mlm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import greenecomall.common.web.RestExceptionHandler;
import greenecomall.common.web.security.ServiceSecurityConfiguration;

/**
 * MLM-аккаунты, окно активации, реферальное дерево, бонусы, интеграция с внешним MLM-бэком
 */
@SpringBootApplication
@Import({RestExceptionHandler.class, ServiceSecurityConfiguration.class})
public class MlmApplication {

    public static void main(String[] args) {
        SpringApplication.run(MlmApplication.class, args);
    }
}
