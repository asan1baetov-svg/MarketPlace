package greenecomall.auth.web.dto;

import greenecomall.auth.token.IssuedTokens;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {

    public static TokenResponse from(IssuedTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
