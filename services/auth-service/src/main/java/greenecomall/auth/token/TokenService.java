package greenecomall.auth.token;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.domain.ClientType;
import greenecomall.auth.domain.MlmSsoIdentity;
import greenecomall.auth.domain.RefreshToken;
import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.config.AuthProperties;
import greenecomall.auth.repo.MlmSsoIdentityRepository;
import greenecomall.auth.repo.RefreshTokenRepository;
import greenecomall.auth.support.Hashing;
import greenecomall.common.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Выпуск и ротация токенов.
 *
 * Refresh хранится только хэшем. При ротации текущий токен помечается revoked и ссылается на новый.
 * Предъявление уже отозванного (ротированного) токена трактуется как компрометация — гасится
 * вся активная цепочка пользователя.
 */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenRepository refreshTokens;
    private final MlmSsoIdentityRepository mlmIdentities;
    private final AuthProperties props;
    private final Clock clock;

    public TokenService(AccessTokenIssuer accessTokenIssuer,
                        RefreshTokenRepository refreshTokens,
                        MlmSsoIdentityRepository mlmIdentities,
                        AuthProperties props,
                        Clock clock) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokens = refreshTokens;
        this.mlmIdentities = mlmIdentities;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public IssuedTokens issueFor(User user) {
        String rawRefresh = newRawToken();
        persistRefreshToken(user, rawRefresh);
        AccessTokenIssuer.AccessToken access =
                accessTokenIssuer.issue(TokenSubject.of(user, resolveMlmUserId(user)));
        return new IssuedTokens(access.value(), rawRefresh, access.expiresInSeconds());
    }

    /**
     * noRollbackFor: при обнаружении переиспользования нужно, чтобы массовый revoke зафиксировался,
     * несмотря на то, что метод завершается ошибкой.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public IssuedTokens refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new DomainException(AuthErrors.REFRESH_INVALID, "refresh token not recognised"));

        if (current.isRevoked()) {
            refreshTokens.revokeAllActiveForUser(current.getUser().getId());
            throw new DomainException(AuthErrors.REFRESH_REUSED, "refresh token reuse detected");
        }
        if (current.isExpired(now)) {
            throw new DomainException(AuthErrors.REFRESH_INVALID, "refresh token expired");
        }

        User user = current.getUser();
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new DomainException(AuthErrors.USER_BLOCKED, "user is blocked");
        }

        String newRaw = newRawToken();
        RefreshToken next = persistRefreshToken(user, newRaw);
        current.rotateTo(next.getId());

        AccessTokenIssuer.AccessToken access =
                accessTokenIssuer.issue(TokenSubject.of(user, resolveMlmUserId(user)));
        return new IssuedTokens(access.value(), newRaw, access.expiresInSeconds());
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256Hex(rawRefreshToken)).ifPresent(RefreshToken::revoke);
    }

    private RefreshToken persistRefreshToken(User user, String rawToken) {
        RefreshToken token = new RefreshToken(
                user, Hashing.sha256Hex(rawToken), clock.instant().plus(props.refresh().ttl()));
        return refreshTokens.save(token);
    }

    private String resolveMlmUserId(User user) {
        if (user.getClientType() != ClientType.INTERNAL_MLM) {
            return null;
        }
        return mlmIdentities.findById(user.getId())
                .map(MlmSsoIdentity::getMlmUserId)
                .orElse(null);
    }

    private static String newRawToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
