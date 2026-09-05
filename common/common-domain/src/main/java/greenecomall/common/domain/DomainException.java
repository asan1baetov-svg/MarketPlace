package greenecomall.common.domain;

/**
 * Базовая доменная ошибка. {@code code} — стабильный машиночитаемый код
 * (например, {@code order.stock_not_available}), пригодный для i18n на клиенте.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
