package greenecomall.courier.config;

import greenecomall.common.web.security.GemSecurityProperties;
import greenecomall.common.web.security.InternalCalls;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CourierProperties.class)
public class SupportConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** RestClient к order-service ({@code /internal/**}): адрес доставки и состав посылки. */
    @Bean
    public RestClient orderRestClient(@Value("${courier.order-base-url:http://localhost:8083}") String baseUrl,
                                      GemSecurityProperties security) {
        return RestClient.builder().baseUrl(baseUrl)
                .requestInterceptor(InternalCalls.internalToken(security)).build();
    }

    /** RestClient к catalog-service ({@code /internal/**}): адрес и телефон магазина для забора. */
    @Bean
    public RestClient catalogRestClient(@Value("${courier.catalog-base-url:http://localhost:8082}") String baseUrl,
                                        GemSecurityProperties security) {
        return RestClient.builder().baseUrl(baseUrl)
                .requestInterceptor(InternalCalls.internalToken(security)).build();
    }
}
