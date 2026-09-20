package greenecomall.order.config;

import greenecomall.common.web.security.GemSecurityProperties;
import greenecomall.common.web.security.InternalCalls;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrderProperties.class)
public class SupportConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * RestClient к catalog-service: резолв цены, резерв/релиз/коммит остатков, владелец магазина.
     * {@code /internal/**} catalog-service требует {@code X-Internal-Token}.
     */
    @Bean
    public RestClient catalogRestClient(OrderProperties properties, GemSecurityProperties security) {
        return RestClient.builder()
                .baseUrl(properties.catalogBaseUrl())
                .requestInterceptor(InternalCalls.internalToken(security))
                .build();
    }
}
