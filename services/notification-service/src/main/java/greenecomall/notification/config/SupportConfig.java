package greenecomall.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class SupportConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
