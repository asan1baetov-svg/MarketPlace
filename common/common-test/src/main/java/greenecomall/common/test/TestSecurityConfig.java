package greenecomall.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Подменяет декодер JWKS auth-service локальным ключом {@link TestJwts}: {@code @Import(TestSecurityConfig.class)}
 * в {@code @SpringBootTest}/{@code @WebMvcTest}, запросы — с {@code Authorization: }{@link TestJwts#bearer}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfig {

    /** Служебный токен, который тесты кладут в {@code gem.security.internal-token}. */
    public static final String INTERNAL_TOKEN = "test-internal-token";

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return TestJwts.decoder();
    }
}
