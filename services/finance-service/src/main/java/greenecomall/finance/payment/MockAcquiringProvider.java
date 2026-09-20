package greenecomall.finance.payment;

import greenecomall.finance.config.FinanceProperties;
import greenecomall.finance.domain.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Заглушка эквайринга для локальной разработки и тестов. «Оплата» подтверждается ручным POST
 * на {@code /webhooks/acquiring/mock}; подпись — HMAC-SHA256(secret, body) в hex.
 */
@Component
@ConditionalOnProperty(prefix = "finance.acquiring", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAcquiringProvider implements PaymentProvider {

    public static final String SIGNATURE_HEADER = "X-Signature";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final FinanceProperties properties;

    public MockAcquiringProvider(FinanceProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public HostedPage initiate(Payment payment) {
        String providerPaymentId = "mock_" + UUID.randomUUID();
        return new HostedPage(providerPaymentId,
                "http://localhost:8084/mock-acquiring/pay/" + providerPaymentId);
    }

    /** Тело: {@code {"providerEventId","providerPaymentId","status","reason"}}, подпись — {@value #SIGNATURE_HEADER}. */
    @Override
    public WebhookEvent parseWebhook(HttpHeaders headers, String rawBody) {
        boolean valid = verifySignature(headers.getFirst(SIGNATURE_HEADER), rawBody);
        JsonNode body = JSON.readTree(rawBody);
        return new WebhookEvent(valid, text(body, "providerEventId"), text(body, "providerPaymentId"),
                text(body, "status"), text(body, "reason"), null);
    }

    @Override
    public Optional<String> refund(Payment payment, long amountMinor) {
        return Optional.of("mock_refund_" + UUID.randomUUID());
    }

    boolean verifySignature(String signatureHeader, String rawBody) {
        if (signatureHeader == null || rawBody == null) {
            return false;
        }
        return constantTimeEquals(hmacSha256Hex(properties.mockWebhookSecret(), rawBody), signatureHeader.trim());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    public static String hmacSha256Hex(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("cannot compute HMAC", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
