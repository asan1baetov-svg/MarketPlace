package greenecomall.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Две независимые цепочки:
 *  - {@code /internal/**}: не через JWT, а по {@code X-Internal-Token} (см. {@link InternalTokenFilter});
 *  - всё остальное: публичные auth-эндпоинты без токена, {@code /auth/me} и прочее — с Bearer JWT
 *    (ключ проверки — {@link JwtConfig}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_POST_PATHS = {
            "/auth/register", "/auth/otp/request", "/auth/otp/verify",
            "/auth/login", "/auth/token/refresh", "/auth/logout", "/auth/sso/mlm"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http, AuthProperties props) throws Exception {
        String internalToken = props.internal() != null ? props.internal().token() : null;
        return http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalTokenFilter(internalToken), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/oauth2/jwks", "/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
