package greenecomall.auth.token;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.config.AuthProperties;
import greenecomall.auth.domain.RefreshToken;
import greenecomall.auth.domain.User;
import greenecomall.auth.repo.MlmSsoIdentityRepository;
import greenecomall.auth.repo.RefreshTokenRepository;
import greenecomall.auth.support.Hashing;
import greenecomall.common.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private AccessTokenIssuer accessTokenIssuer;
    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private MlmSsoIdentityRepository mlmIdentities;

    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AuthProperties props = new AuthProperties(
                null, new AuthProperties.Refresh(Duration.ofDays(30)), null, null, null, null);
        tokenService = new TokenService(accessTokenIssuer, refreshTokens, mlmIdentities, props, clock);
        user = User.forLocalRegistration("client@example.com", null, "hash", "ru");

        when(accessTokenIssuer.issue(any()))
                .thenReturn(new AccessTokenIssuer.AccessToken("signed-jwt", 900));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void issueFor_returnsAccessAndRefreshTokens() {
        IssuedTokens tokens = tokenService.issueFor(user);

        assertThat(tokens.accessToken()).isEqualTo("signed-jwt");
        assertThat(tokens.expiresIn()).isEqualTo(900);
        assertThat(tokens.refreshToken()).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(Hashing.sha256Hex(tokens.refreshToken()));
    }

    @Test
    void refresh_rotatesToken_andRevokesThePrevious() {
        IssuedTokens issued = tokenService.issueFor(user);
        RefreshToken persisted = captureLastSaved();
        when(refreshTokens.findByTokenHash(Hashing.sha256Hex(issued.refreshToken())))
                .thenReturn(Optional.of(persisted));

        IssuedTokens refreshed = tokenService.refresh(issued.refreshToken());

        assertThat(refreshed.refreshToken()).isNotEqualTo(issued.refreshToken());
        assertThat(persisted.isRevoked()).isTrue();
    }

    @Test
    void refresh_withAlreadyRevokedToken_revokesWholeChainAndFails() {
        IssuedTokens issued = tokenService.issueFor(user);
        RefreshToken persisted = captureLastSaved();
        when(refreshTokens.findByTokenHash(Hashing.sha256Hex(issued.refreshToken())))
                .thenReturn(Optional.of(persisted));

        // first refresh rotates and marks `persisted` as revoked
        tokenService.refresh(issued.refreshToken());

        // presenting the same (now revoked) raw refresh token again = reuse of a compromised token
        assertThatThrownBy(() -> tokenService.refresh(issued.refreshToken()))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.REFRESH_REUSED);

        verify(refreshTokens).revokeAllActiveForUser(any());
    }

    private RefreshToken captureLastSaved() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
