package greenecomall.auth.token;

/**
 * Порт выпуска access-токена. Сейчас реализуется через Spring Security {@code JwtEncoder} (RS256);
 * при переезде на внешний OAuth2-провайдер (Keycloak и т.п.) подменяется реализация, вызовы не меняются.
 */
public interface AccessTokenIssuer {

    AccessToken issue(TokenSubject subject);

    record AccessToken(String value, long expiresInSeconds) {
    }
}
