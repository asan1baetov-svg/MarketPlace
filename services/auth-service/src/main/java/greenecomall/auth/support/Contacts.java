package greenecomall.auth.support;

import greenecomall.auth.domain.OtpChannel;

/**
 * Нормализация контактов (email/телефон) перед сравнением и хранением.
 */
public final class Contacts {

    private Contacts() {
    }

    /** trim + lower-case; пустая строка → null. Форматная валидация — на уровне контроллера. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** По формату нормализованного target-а определяет канал доставки OTP. */
    public static OtpChannel inferChannel(String normalizedTarget) {
        return normalizedTarget != null && normalizedTarget.contains("@") ? OtpChannel.EMAIL : OtpChannel.SMS;
    }
}
