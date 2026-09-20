package greenecomall.finance.config;

import greenecomall.common.web.security.GemSecurityProperties;
import greenecomall.finance.finik.FinikProperties;
import greenecomall.common.web.security.InternalCalls;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({FinanceProperties.class, FinikProperties.class})
public class SupportConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** RestClient к mlm-service ({@code /internal/mlm/**}, с {@code X-Internal-Token}). */
    @Bean
    public RestClient mlmRestClient(FinanceProperties properties, GemSecurityProperties security) {
        return RestClient.builder()
                .baseUrl(properties.mlmBaseUrl())
                .requestInterceptor(InternalCalls.internalToken(security))
                .build();
    }
}
