package greenecomall.mlm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MlmProperties.class)
public class SupportConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RestClient mlmBackendRestClient(MlmProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        if (properties.sync().enabled()) {
            builder.baseUrl(properties.sync().baseUrl());
        }
        return builder.build();
    }
}
