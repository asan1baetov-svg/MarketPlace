package greenecomall.mlm.config;

import greenecomall.common.web.security.ServiceAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** Входящий webhook MLM-системы без JWT: подлинность проверяется HMAC-подписью в контроллере. */
@Configuration
public class MlmSecurityRules {

    @Bean
    ServiceAuthorizationRules mlmAuthorizationRules() {
        return rules -> rules.requestMatchers(HttpMethod.POST, "/webhooks/mlm/**").permitAll();
    }
}
