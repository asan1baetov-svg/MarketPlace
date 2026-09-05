package greenecomall.auth.token;

/**
 * Пара токенов, отдаваемая клиенту.
 *
 * @param accessToken  подписанный JWT (RS256), короткоживущий
 * @param refreshToken непрозрачная строка, показывается один раз; в БД только её хэш
 * @param expiresIn    время жизни access-токена в секундах
 */
public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {
}
