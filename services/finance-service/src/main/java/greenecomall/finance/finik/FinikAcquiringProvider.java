package greenecomall.finance.finik;

import greenecomall.common.domain.DomainException;
import greenecomall.finance.FinanceErrors;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.domain.PaymentType;
import greenecomall.finance.payment.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Приём оплаты через Finik QR на счёт платформы. Клиент платит полную цену (с наценкой);
 * магазинам их часть уходит переводом ({@link FinikTransferGateway}), комиссия остаётся на счёте.
 *
 * <p>{@code PaymentId} у Finik = наш {@code payment.id}: он же возвращается в webhook в
 * {@code fields.paymentId}. API возврата у Finik нет — возвраты делаются вручную в кабинете.
 */
@Component
@ConditionalOnProperty(prefix = "finance.acquiring", name = "provider", havingValue = "finik")
public class FinikAcquiringProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(FinikAcquiringProvider.class);
    private static final String CREATE_PATH = "/v1/payment";

    private final FinikProperties.Acquiring cfg;
    private final PrivateKey privateKey;
    private final PublicKey finikPublicKey;
    private final ObjectMapper objectMapper;
    private final FinikHttp http;

    public FinikAcquiringProvider(FinikProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.cfg = properties.acquiring();
        this.privateKey = FinikSigner.privateKey(cfg.privateKey());
        this.finikPublicKey = FinikSigner.publicKey(properties.publicKey());
        this.objectMapper = objectMapper;
        this.http = new FinikHttp(clock);
    }

    @Override
    public String name() {
        return "finik";
    }

    @Override
    public HostedPage initiate(Payment payment) {
        if (payment.getAmountMinor() % 100 != 0) {
            throw new DomainException(FinanceErrors.PAYMENT_AMOUNT_INVALID,
                    "Finik accepts whole units only, got " + payment.getAmountMinor() + " minor");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountId", cfg.accountId());
        data.put("merchantCategoryCode", cfg.merchantCategoryCode());
        data.put("name_en", cfg.qrName());
        data.put("description", payment.getType() == PaymentType.ORDER ? "Green Eco Mall: заказ" : "Green Eco Mall: доступ MLM");
        data.put("webhookUrl", cfg.webhookUrl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Amount", payment.getAmountMinor() / 100);
        body.put("CardType", "FINIK_QR");
        body.put("PaymentId", payment.getId().toString());
        body.put("RedirectUrl", cfg.redirectUrl());
        body.put("Data", data);

        FinikHttp.Response response = http.post(cfg.baseUrl(), CREATE_PATH, cfg.apiKey(), privateKey,
                objectMapper.writeValueAsString(body));
        String url = paymentUrl(response)
                .orElseThrow(() -> new DomainException(FinanceErrors.ACQUIRING_UNAVAILABLE,
                        "unexpected Finik response " + response.status() + ": " + response.body()));
        log.info("Finik QR created for payment {}", payment.getId());
        return new HostedPage(payment.getId().toString(), url);
    }

    /**
     * Подпись webhook: {@code post\n<path>\nhost:<host>&x-api-timestamp:<ts>\n<body>}. Хост и путь —
     * из настроенного {@code webhookUrl}: Finik подписывает публичный адрес, а не адрес за гейтвеем.
     */
    @Override
    public WebhookEvent parseWebhook(HttpHeaders headers, String rawBody) {
        URI hook = URI.create(cfg.webhookUrl());
        String timestamp = headers.getFirst("X-Api-Timestamp");
        String canonical = FinikSigner.canonical("POST", hook.getRawPath(), hook.getHost(),
                Map.of("x-api-timestamp", timestamp == null ? "" : timestamp), rawBody);
        boolean valid = FinikSigner.verify(canonical, headers.getFirst("Signature"), finikPublicKey);

        JsonNode body = objectMapper.readTree(rawBody);
        String eventId = text(body, "transactionId") != null ? text(body, "transactionId") : text(body, "id");
        JsonNode fields = body.get("fields");
        String paymentId = fields == null ? null : text(fields, "paymentId");
        Long amountMinor = body.hasNonNull("amount")
                ? new BigDecimal(body.get("amount").asString()).movePointRight(2).longValueExact() : null;
        return new WebhookEvent(valid, eventId, paymentId, text(body, "status"), text(body, "errorMessage"), amountMinor);
    }

    @Override
    public Optional<String> refund(Payment payment, long amountMinor) {
        return Optional.empty();
    }

    private Optional<String> paymentUrl(FinikHttp.Response response) {
        if (response.status() == 302 || response.status() == 301) {
            return response.location();
        }
        if (response.status() == 200 && response.body() != null) {
            String body = response.body().trim();
            if (body.startsWith("http")) {
                return Optional.of(body);
            }
            JsonNode json = objectMapper.readTree(body);
            for (String key : new String[]{"paymentUrl", "url", "link", "redirectUrl", "qrUrl"}) {
                if (json.hasNonNull(key)) {
                    return Optional.of(json.get(key).asString());
                }
            }
        }
        return Optional.empty();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }
}
