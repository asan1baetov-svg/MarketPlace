package greenecomall.auth.web.dto;

import greenecomall.auth.mlm.MlmSsoService;

public record SsoTokenResponse(String accessToken, String refreshToken, long expiresIn, boolean created) {

    public static SsoTokenResponse from(MlmSsoService.SsoLogin login) {
        return new SsoTokenResponse(
                login.tokens().accessToken(), login.tokens().refreshToken(),
                login.tokens().expiresIn(), login.created());
    }
}
