package greenecomall.common.test;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Выпуск access-JWT в формате auth-service (RS256, claims {@code sub}/{@code roles}/{@code client_type})
 * локальным ключом. В тесте: {@code @Primary JwtDecoder} = {@link #decoder()}, заголовок
 * {@code Authorization: Bearer } + {@link #token(UUID, String...)}.
 */
public final class TestJwts {

    private static final RSAKey KEY = generate();
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(KEY)));

    private TestJwts() {
    }

    public static JwtDecoder decoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String token(UUID userId, String... roles) {
        return mlmToken(userId, null, roles);
    }

    /** Токен внутреннего (MLM) клиента: {@code client_type=internal_mlm} и claim {@code mlm_user_id}. */
    public static String mlmToken(UUID userId, String mlmUserId, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .audience(List.of("green-eco-mall"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .subject(userId.toString())
                .claim("roles", List.of(roles))
                .claim("client_type", mlmUserId != null ? "internal_mlm" : "external");
        if (mlmUserId != null) {
            claims.claim("mlm_user_id", mlmUserId);
        }
        return ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    public static String bearer(UUID userId, String... roles) {
        return "Bearer " + token(userId, roles);
    }

    private static RSAKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("cannot generate RSA key", e);
        }
    }
}
