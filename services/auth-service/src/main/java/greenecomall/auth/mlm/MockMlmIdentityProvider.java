package greenecomall.auth.mlm;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.config.AuthProperties;
import greenecomall.common.domain.DomainException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Реализация проверки SSO-токена для локальной разработки: HS256 + общий секрет
 * (совпадает с {@code infra/mock-mlm/app.py}). Проверяет подпись, iss/aud/exp/nbf.
 */
@Component
public class MockMlmIdentityProvider implements MlmIdentityProvider {

    private static final int MIN_SECRET_BYTES = 32; // HS256 требует ключ >= 256 бит

    private final NimbusJwtDecoder decoder;

    public MockMlmIdentityProvider(AuthProperties props) {
        AuthProperties.MlmSso cfg = props.mlmSso();
        byte[] secret = cfg.sharedSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("auth.mlm-sso.shared-secret must be at least "
                    + MIN_SECRET_BYTES + " bytes for HS256");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");

        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(cfg.clockSkew()),
                new JwtIssuerValidator(cfg.issuer()),
                audienceContains(cfg.audience()))));
        this.decoder = nimbus;
    }

    @Override
    public MlmIdentity verifyAndExtract(String rawToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(rawToken);
        } catch (JwtException e) {
            throw new DomainException(AuthErrors.SSO_TOKEN_INVALID, "invalid MLM SSO token: " + e.getMessage());
        }

        String mlmUserId = firstNonBlank(jwt.getClaimAsString("mlm_user_id"), jwt.getSubject());
        if (mlmUserId == null) {
            throw new DomainException(AuthErrors.SSO_TOKEN_INVALID, "mlm_user_id / sub missing");
        }
        if (jwt.getId() == null) {
            throw new DomainException(AuthErrors.SSO_TOKEN_INVALID, "jti missing");
        }

        return new MlmIdentity(
                jwt.getId(),
                jwt.getIssuedAt(),
                mlmUserId,
                jwt.getClaimAsString("referral_code"),
                firstNonBlank(jwt.getClaimAsString("upline_id"), jwt.getClaimAsString("upline_mlm_user_id")),
                jwt.getClaimAsString("access_status"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("phone"),
                jwt.getClaimAsString("full_name"),
                orDefault(jwt.getClaimAsString("locale"), "ru"),
                jwt.getClaimAsString("country_code"),
                jwt.getClaimAsString("city"));
    }

    private static OAuth2TokenValidator<Jwt> audienceContains(String expected) {
        return new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(expected));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
