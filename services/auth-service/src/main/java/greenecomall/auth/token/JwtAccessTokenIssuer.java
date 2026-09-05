package greenecomall.auth.token;

import greenecomall.auth.config.AuthProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final AuthProperties props;
    private final Clock clock;

    public JwtAccessTokenIssuer(JwtEncoder encoder, AuthProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public AccessToken issue(TokenSubject subject) {
        Instant now = clock.instant();
        Duration ttl = props.jwt().accessTokenTtl();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .audience(List.of(props.jwt().audience()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .subject(subject.userId().toString())
                .claim("roles", List.copyOf(subject.roles()))
                .claim("client_type", subject.clientType());
        if (subject.mlmUserId() != null) {
            claims.claim("mlm_user_id", subject.mlmUserId());
        }

        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
        return new AccessToken(token, ttl.toSeconds());
    }
}
