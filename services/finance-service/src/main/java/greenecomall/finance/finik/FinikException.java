package greenecomall.finance.finik;

/** Сбой обращения к Finik (сеть, ответ не 2xx/3xx, отказ по получателю). */
public class FinikException extends RuntimeException {

    public FinikException(String message) {
        super(message);
    }

    public FinikException(String message, Throwable cause) {
        super(message, cause);
    }
}
