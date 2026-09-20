package greenecomall.catalog.config;

import greenecomall.catalog.photo.PhotoProperties;
import greenecomall.catalog.text.ProofreadProperties;
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
@EnableConfigurationProperties({PhotoProperties.class, ProofreadProperties.class})
public class SupportConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** RestClient к order-service ({@code /internal/**}, с {@code X-Internal-Token}): проверка покупки для отзывов. */
    @Bean
    public RestClient orderRestClient(@Value("${catalog.order-base-url:http://localhost:8083}") String baseUrl,
                                      GemSecurityProperties security) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(InternalCalls.internalToken(security))
                .build();
    }
}
