package greenecomall.auth.domain;

/**
 * Тип клиента. EXTERNAL — обычный покупатель, регистрируется на маркетплейсе.
 * INTERNAL_MLM — пришёл из внешней MLM-системы по SSO, аккаунт создан автоматически.
 */
public enum ClientType {
    EXTERNAL,
    INTERNAL_MLM
}
