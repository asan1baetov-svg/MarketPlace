package greenecomall.auth.mlm;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import greenecomall.auth.AuthErrors;
import greenecomall.auth.config.AuthProperties;
import greenecomall.common.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockMlmIdentityProviderTest {

    private static final String SECRET = "unit-test-mlm-shared-secret-with-32-plus-bytes";
    private static final String ISSUER = "mock-mlm";
    private static final String AUDIENCE = "green-eco-mall";

    private final MockMlmIdentityProvider provider = new MockMlmIdentityProvider(new AuthProperties(
            null, null, null,
            new AuthProperties.MlmSso(SECRET, ISSUER, AUDIENCE, Duration.ofSeconds(60)),
            null, null));

    @Test
    void validToken_isParsedIntoIdentity() throws Exception {
        String token = sign(claims(ISSUER, AUDIENCE, "U1", 60), SECRET);

        MlmIdentity identity = provider.verifyAndExtract(token);

        assertThat(identity.mlmUserId()).isEqualTo("U1");
        assertThat(identity.referralCode()).isEqualTo("REF1");
        assertThat(identity.uplineMlmUserId()).isEqualTo("U0");
        assertThat(identity.jti()).isNotBlank();
    }

    @Test
    void badSignature_isRejected() throws Exception {
        String token = sign(claims(ISSUER, AUDIENCE, "U1", 60), "a-completely-different-secret-32bytes!!");

        assertThatThrownBy(() -> provider.verifyAndExtract(token))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.SSO_TOKEN_INVALID);
    }

    @Test
    void wrongAudience_isRejected() throws Exception {
        String token = sign(claims(ISSUER, "someone-else", "U1", 60), SECRET);

        assertThatThrownBy(() -> provider.verifyAndExtract(token))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.SSO_TOKEN_INVALID);
    }

    @Test
    void expiredToken_isRejected() throws Exception {
        String token = sign(claims(ISSUER, AUDIENCE, "U1", -60), SECRET);

        assertThatThrownBy(() -> provider.verifyAndExtract(token))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.SSO_TOKEN_INVALID);
    }

    @Test
    void missingJti_isRejected() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("U1")
                .claim("mlm_user_id", "U1")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .build();

        String token = sign(claims, SECRET);

        assertThatThrownBy(() -> provider.verifyAndExtract(token))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.SSO_TOKEN_INVALID);
    }

    private static JWTClaimsSet claims(String issuer, String audience, String mlmUserId, long expiresInSeconds) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(mlmUserId)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                .claim("mlm_user_id", mlmUserId)
                .claim("referral_code", "REF1")
                .claim("upline_id", "U0")
                .claim("access_status", "access_paid")
                .claim("email", "mlm-user@example.com")
                .claim("locale", "ru")
                .build();
    }

    private static String sign(JWTClaimsSet claims, String secret) throws Exception {
        JWSSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
