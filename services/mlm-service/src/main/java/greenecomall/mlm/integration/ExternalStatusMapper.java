package greenecomall.mlm.integration;

/**
 * Перевод статуса аккаунта внешней MLM-системы в понятия маркетплейса.
 * Во внешнем бэке {@code AccountStatus} = {@code PENDING | ACTIVE | BLOCKED}: {@code ACTIVE}
 * означает, что входной взнос оплачен — для маркетплейса это «доступ оплачен», дальше
 * включается окно «покупка на X за Y». Также принимаются значения дефолтного контракта
 * ({@code access_paid}, {@code active}, {@code expired}, {@code none}) — mock-mlm выдаёт их.
 */
public final class ExternalStatusMapper {

    public enum Meaning {NOT_PAID, ACCESS_PAID, BLOCKED, UNKNOWN}

    private ExternalStatusMapper() {
    }

    public static Meaning map(String externalStatus) {
        if (externalStatus == null || externalStatus.isBlank()) {
            return Meaning.UNKNOWN;
        }
        return switch (externalStatus.trim().toUpperCase()) {
            case "ACTIVE", "ACCESS_PAID", "PAID" -> Meaning.ACCESS_PAID;
            case "PENDING", "NONE", "EXPIRED" -> Meaning.NOT_PAID;
            case "BLOCKED" -> Meaning.BLOCKED;
            default -> Meaning.UNKNOWN;
        };
    }
}
