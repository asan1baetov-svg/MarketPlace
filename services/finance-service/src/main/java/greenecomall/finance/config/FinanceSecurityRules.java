package greenecomall.finance.config;

import greenecomall.common.web.security.ServiceAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Без JWT: webhook эквайринга (подлинность — HMAC-подпись) и симулятор страницы оплаты
 * mock-эквайринга (на неё клиента редиректит hosted-page URL; в проде провайдер реальный).
 */
@Configuration
public class FinanceSecurityRules {

    @Bean
    ServiceAuthorizationRules financeAuthorizationRules() {
        return rules -> rules
                .requestMatchers(HttpMethod.POST, "/webhooks/acquiring/**").permitAll()
                .requestMatchers("/mock-acquiring/**").permitAll();
    }
}
