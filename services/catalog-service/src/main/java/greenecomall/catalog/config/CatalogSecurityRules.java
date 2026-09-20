package greenecomall.catalog.config;

import greenecomall.common.web.security.ServiceAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Витрина, категории, гео-справочники и карточка магазина — публичные (гость видит витрину города).
 * Кабинет магазина ({@code /shops}, {@code /products/**}) — любой аутентифицированный: роль SHOP
 * появляется только после модерации, а владение проверяется по {@code sub} токена.
 */
@Configuration
public class CatalogSecurityRules {

    @Bean
    ServiceAuthorizationRules catalogAuthorizationRules() {
        return rules -> rules.requestMatchers(HttpMethod.GET, "/catalog/**").permitAll();
    }
}
