package greenecomall.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Ключевой материал для подписи access-JWT (RS256).
 *
 * TODO(prod): сейчас ключ генерируется эфемерно на каждый запуск — после рестарта прежние
 * токены становятся невалидными. В проде грузить приватный ключ из секрет-менеджера/волта,
 * держать пул из нескольких kid и ротировать их с перекрытием.
 */
@Configuration
public class JwtConfig {

    @Bean
    public RSAKey rsaSigningKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("cannot generate RSA signing key", e);
        }
    }

    /** Отдаётся наружу через будущий эндпоинт {@code GET /oauth2/jwks}. */
    @Bean
    public JWKSet jwkSet(RSAKey rsaSigningKey) {
        return new JWKSet(rsaSigningKey.toPublicJWK());
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaSigningKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaSigningKey) {
        try {
            return NimbusJwtDecoder.withPublicKey(rsaSigningKey.toRSAPublicKey()).build();
        } catch (Exception e) {
            throw new IllegalStateException("cannot build JwtDecoder", e);
        }
    }
}
