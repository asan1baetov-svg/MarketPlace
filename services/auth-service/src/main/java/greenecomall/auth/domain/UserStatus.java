package greenecomall.auth.domain;

/**
 * Жизненный цикл учётной записи.
 * PENDING — зарегистрирован, но не подтвердил контакт (OTP);
 * ACTIVE — подтверждён и может входить;
 * BLOCKED — доступ закрыт администратором.
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    BLOCKED
}
