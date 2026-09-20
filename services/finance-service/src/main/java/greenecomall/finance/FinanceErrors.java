package greenecomall.finance;

/** Коды доменных ошибок finance-service (тот же приём, что {@code CatalogErrors}/{@code OrderErrors}). */
public final class FinanceErrors {

    public static final String PAYMENT_NOT_FOUND = "finance.payment_not_found";
    public static final String PAYMENT_STATUS_INVALID = "finance.payment_status_invalid";
    public static final String PAYMENT_ALREADY_EXISTS = "finance.payment_already_exists";
    public static final String PAYMENT_AMOUNT_INVALID = "finance.payment_amount_invalid";
    public static final String MLM_TARIFF_NOT_FOUND = "finance.mlm_tariff_not_found";
    public static final String MLM_ACCOUNT_REQUIRED = "finance.mlm_account_required";
    public static final String MLM_UNAVAILABLE = "finance.mlm_unavailable";
    public static final String ACQUIRING_UNAVAILABLE = "finance.acquiring_unavailable";

    public static final String WEBHOOK_SIGNATURE_INVALID = "finance.webhook_signature_invalid";
    public static final String WEBHOOK_UNKNOWN_PAYMENT = "finance.webhook_unknown_payment";

    public static final String WALLET_NOT_FOUND = "finance.wallet_not_found";
    public static final String WALLET_INSUFFICIENT_FUNDS = "finance.wallet_insufficient_funds";
    public static final String CURRENCY_MISMATCH = "finance.currency_mismatch";

    public static final String PAYOUT_NOT_FOUND = "finance.payout_not_found";
    public static final String PAYOUT_STATUS_INVALID = "finance.payout_status_invalid";
    public static final String REQUISITE_NOT_FOUND = "finance.requisite_not_found";
    public static final String REQUISITE_STATUS_INVALID = "finance.requisite_status_invalid";
    public static final String REQUISITE_INVALID = "finance.requisite_invalid";

    private FinanceErrors() {
    }
}
