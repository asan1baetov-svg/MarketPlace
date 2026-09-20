package greenecomall.mlm;

/** Коды доменных ошибок mlm-service. */
public final class MlmErrors {

    public static final String ACCOUNT_NOT_FOUND = "mlm.account_not_found";
    public static final String ACCOUNT_BLOCKED = "mlm.account_blocked";
    public static final String ACCESS_STATUS_INVALID = "mlm.access_status_invalid";

    public static final String TARIFF_NOT_FOUND = "mlm.tariff_not_found";
    public static final String NO_DEFAULT_TARIFF = "mlm.no_default_tariff";

    public static final String TARIFF_CONFLICT = "mlm.tariff_conflict";

    public static final String WEBHOOK_SIGNATURE_INVALID = "mlm.webhook_signature_invalid";
    public static final String WEBHOOK_BODY_INVALID = "mlm.webhook_body_invalid";

    private MlmErrors() {
    }
}
