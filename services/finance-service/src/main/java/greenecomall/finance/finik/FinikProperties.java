package greenecomall.finance.finik;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Finik (Averspay): приём оплат по QR (Acquiring API) и переводы магазинам (Payments Gateway).
 * Ключи — PEM-содержимое из переменных окружения, в репозиторий не кладутся.
 *
 * @param publicKey  публичный ключ Finik для проверки подписи webhook (PEM); по умолчанию — боевой
 * @param acquiring  приём оплат клиентов на счёт платформы
 * @param transfer   переводы магазинам с счёта платформы
 */
@ConfigurationProperties(prefix = "finance.finik")
public record FinikProperties(String publicKey, Acquiring acquiring, Transfer transfer) {

    /** Боевой публичный ключ Finik (из документации Averspay; тот же, что в MLM-системе GreenEcoMall). */
    public static final String PRODUCTION_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuF/PUmhMPPidcMxhZBPb
            BSGJoSphmCI+h6ru8fG8guAlcPMVlhs+ThTjw2LHABvciwtpj51ebJ4EqhlySPyT
            hqSfXI6Jp5dPGJNDguxfocohaz98wvT+WAF86DEglZ8dEsfoumojFUy5sTOBdHEu
            g94B4BbrJvjmBa1YIx9Azse4HFlWhzZoYPgyQpArhokeHOHIN2QFzJqeriANO+wV
            aUMta2AhRVZHbfyJ36XPhGO6A5FYQWgjzkI65cxZs5LaNFmRx6pjnhjIeVKKgF99
            4OoYCzhuR9QmWkPl7tL4Kd68qa/xHLz0Psnuhm0CStWOYUu3J7ZpzRK8GoEXRcr8
            tQIDAQAB
            -----END PUBLIC KEY-----
            """;

    public FinikProperties {
        if (publicKey == null || publicKey.isBlank()) {
            publicKey = PRODUCTION_PUBLIC_KEY;
        }
        if (acquiring == null) {
            acquiring = new Acquiring(null, null, null, null, null, null, null, null);
        }
        if (transfer == null) {
            transfer = new Transfer(null, null, null, null, null);
        }
    }

    /**
     * @param webhookUrl публичный URL webhook (через api-gateway), он же участвует в подписи Finik:
     *                   хост и путь берутся отсюда, а не из заголовков — за гейтвеем {@code Host} другой
     */
    public record Acquiring(String baseUrl, String apiKey, String accountId, String privateKey,
                            String webhookUrl, String redirectUrl, String merchantCategoryCode, String qrName) {
        public Acquiring {
            if (merchantCategoryCode == null || merchantCategoryCode.isBlank()) {
                merchantCategoryCode = "0742";
            }
            if (qrName == null || qrName.isBlank()) {
                qrName = "GreenEcoMall";
            }
        }
    }

    public record Transfer(String baseUrl, String apiKey, String accountId, String userId, String privateKey) {
        public Transfer {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.paymentsgateway.averspay.kg";
            }
        }
    }
}
