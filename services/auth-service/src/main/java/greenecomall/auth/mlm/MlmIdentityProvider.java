package greenecomall.auth.mlm;

/**
 * Антикоррупционный слой над протоколом входа из внешней MLM-системы.
 * Текущая реализация — {@link MockMlmIdentityProvider} (HS256, общий секрет).
 * Реальный контракт (OIDC / RS256 + JWKS) подключается новой реализацией без правок остального кода.
 * См. docs/ARCHITECTURE.md §5.
 */
public interface MlmIdentityProvider {

    /**
     * Проверяет подпись, {@code iss}/{@code aud}/{@code exp}/{@code nbf} и извлекает данные.
     *
     * @throws greenecomall.common.domain.DomainException с кодом {@code sso.token_invalid} при невалидности
     */
    MlmIdentity verifyAndExtract(String rawToken);
}
