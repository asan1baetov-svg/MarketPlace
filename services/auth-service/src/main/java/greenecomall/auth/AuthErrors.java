package greenecomall.auth;

/**
 * Коды доменных ошибок auth-service. Кладутся в {@code code} у {@code DomainException}
 * и дальше в {@code ProblemDetail} для i18n на клиенте.
 */
public final class AuthErrors {

    public static final String CONTACT_TAKEN = "auth.contact_taken";
    public static final String CREDENTIALS_INVALID = "auth.credentials_invalid";
    public static final String USER_BLOCKED = "auth.user_blocked";
    public static final String USER_NOT_ACTIVE = "auth.user_not_active";

    public static final String OTP_INVALID = "auth.otp_invalid";
    public static final String OTP_EXPIRED = "auth.otp_expired";
    public static final String OTP_ATTEMPTS_EXCEEDED = "auth.otp_attempts_exceeded";
    public static final String OTP_TOO_FREQUENT = "auth.otp_too_frequent";
    public static final String OTP_PURPOSE_UNSUPPORTED = "auth.otp_purpose_unsupported";

    public static final String REFRESH_INVALID = "auth.refresh_invalid";
    public static final String REFRESH_REUSED = "auth.refresh_reused";

    public static final String SSO_TOKEN_INVALID = "sso.token_invalid";
    public static final String SSO_TOKEN_REPLAYED = "sso.token_replayed";

    private AuthErrors() {
    }
}
