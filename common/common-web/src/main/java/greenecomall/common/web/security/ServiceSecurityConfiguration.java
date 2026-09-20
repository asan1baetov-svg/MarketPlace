package greenecomall.common.web.security;

import greenecomall.common.security.Roles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Единая модель безопасности сервисов за api-gateway (docs/ARCHITECTURE.md §2.8, R10):
 * <ol>
 *   <li>{@code /internal/**} — только с {@code X-Internal-Token} ({@link InternalTokenFilter}), JWT не нужен;</li>
 *   <li>всё остальное — resource server: сервис сам проверяет подпись access-JWT по JWKS auth-service,
 *       {@code iss}, {@code aud}, срок. Заголовкам {@code X-User-*} от гейтвея сервисы не доверяют —
 *       идентичность берётся только из проверенного токена ({@link AuthPrincipalArgumentResolver}).</li>
 * </ol>
 * Подключается через {@code @Import} в {@code *Application}; правила сервиса — бин {@link ServiceAuthorizationRules}.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(GemSecurityProperties.class)
public class ServiceSecurityConfiguration implements WebMvcConfigurer {

    private static final String[] ACTUATOR_PUBLIC = {"/actuator/health/**", "/actuator/info", "/actuator/prometheus"};

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, GemSecurityProperties props)
            throws Exception {
        return http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalTokenFilter(props.internalToken()), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(InternalTokenFilter.ROLE_INTERNAL))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(SecurityProblems.unauthorized())
                        .accessDeniedHandler(SecurityProblems.forbidden()))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> decoders,
                                                      ObjectProvider<ServiceAuthorizationRules> serviceRules)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(ACTUATOR_PUBLIC).permitAll();
                    auth.requestMatchers("/admin/**").hasAnyRole(Roles.ADMIN, Roles.SUPER_ADMIN);
                    serviceRules.orderedStream().forEach(rules -> rules.configure(auth));
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(decoders.getIfUnique()).jwtAuthenticationConverter(rolesConverter()))
                        .authenticationEntryPoint(SecurityProblems.unauthorized())
                        .accessDeniedHandler(SecurityProblems.forbidden()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(SecurityProblems.unauthorized())
                        .accessDeniedHandler(SecurityProblems.forbidden()))
                .build();
    }

    /** Декодер по JWKS auth-service. В тестах перекрывается {@code @Primary}-бином с локальным ключом. */
    @Bean
    public JwtDecoder gemJwtDecoder(GemSecurityProperties props) {
        GemSecurityProperties.Jwt cfg = props.jwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(cfg.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(cfg.clockSkew()),
                new JwtIssuerValidator(cfg.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(cfg.audience())))));
        return decoder;
    }

    /** Claim {@code roles} → authorities {@code ROLE_*}; principal name = {@code sub}. */
    static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix(Roles.AUTHORITY_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthPrincipalArgumentResolver());
    }
}
