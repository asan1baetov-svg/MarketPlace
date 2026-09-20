package greenecomall.finance.finik;

import greenecomall.finance.domain.PayoutRequisite;
import greenecomall.finance.wallet.PayoutGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.PrivateKey;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Переводы магазинам через Finik Payments Gateway: проверка получателя ({@code /v2/recipient}),
 * затем платёж ({@code /v2/payment}). Логика и формат полей — из MLM-системы GreenEcoMall
 * ({@code FinikTransferApiService}): MBank принимает другой набор полей, чем остальные банки.
 */
@Component
@ConditionalOnProperty(prefix = "finance.payout", name = "provider", havingValue = "finik")
public class FinikTransferGateway implements PayoutGateway {

    private static final Logger log = LoggerFactory.getLogger(FinikTransferGateway.class);

    private final FinikProperties.Transfer cfg;
    private final PrivateKey privateKey;
    private final ObjectMapper objectMapper;
    private final FinikHttp http;

    public FinikTransferGateway(FinikProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.cfg = properties.transfer();
        this.privateKey = FinikSigner.privateKey(cfg.privateKey());
        this.objectMapper = objectMapper;
        this.http = new FinikHttp(clock);
    }

    @Override
    public String transfer(PayoutRequisite requisite, long amountMinor, String transactionId, String comment) {
        FinikBank bank = requisite.getBank();
        if (amountMinor % 100 != 0) {
            throw new PayoutRejectedException("transfer amount must be whole som: " + amountMinor);
        }
        int som = Math.toIntExact(amountMinor / 100);
        if (som < bank.minSom() || som > bank.maxSom()) {
            throw new PayoutRejectedException(bank.displayName() + " accepts " + bank.minSom() + ".." + bank.maxSom()
                    + " som per transfer, got " + som);
        }

        Map<String, Object> check = new LinkedHashMap<>();
        check.put("service", bank.serviceId());
        check.put("fields", fields(bank, requisite.getPhone(), som, null));
        requireOk(call("/v2/recipient", check), "recipient check", true);

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("transactionId", transactionId);
        payment.put("accountId", cfg.accountId());
        payment.put("userId", cfg.userId());
        payment.put("service", Map.of("id", bank.serviceId()));
        payment.put("fields", fields(bank, requisite.getPhone(), som, comment));
        JsonNode result = requireOk(call("/v2/payment", payment), "payment", false);
        for (String key : new String[]{"id", "transactionId", "paymentId"}) {
            if (result.hasNonNull(key)) {
                return result.get(key).asString();
            }
        }
        return transactionId;
    }

    private static Map<String, Object> fields(FinikBank bank, String phone, int som, String comment) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (bank.requiresTransactionType()) {
            fields.put("account", phone);
            fields.put("amount", som);
            fields.put("transactionType", "10");
            fields.put("provider", "c2c.mbank.kg");
            fields.put("merchantCode", "9999");
            if (comment != null && !comment.isBlank()) {
                fields.put("qrComment", comment);
            }
        } else {
            fields.put("account.value.persacc", phone);
            if (bank.serviceCode() != null) {
                fields.put("service", bank.serviceCode());
            }
            fields.put("total", som);
            fields.put("qp_comment", comment != null ? comment : " ");
        }
        return fields;
    }

    private FinikHttp.Response call(String path, Map<String, Object> body) {
        String json = objectMapper.writeValueAsString(body);
        log.info("Finik transfer {}: {}", path, json);
        FinikHttp.Response response = http.post(cfg.baseUrl(), path, cfg.apiKey(), privateKey, json);
        log.info("Finik transfer {} -> {} {}", path, response.status(), response.body());
        return response;
    }

    /**
     * 5xx — временный сбой (повторим). 4xx или {@code statusCode} ошибки в теле: на проверке получателя —
     * окончательный отказ (реквизиты неверны), на платеже — тоже окончательный, чтобы не слать повторно
     * перевод, который банк уже отклонил по существу.
     */
    private JsonNode requireOk(FinikHttp.Response response, String step, boolean recipientCheck) {
        if (response.status() >= 500) {
            throw new FinikException("Finik " + step + " HTTP " + response.status() + ": " + response.body());
        }
        JsonNode body = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        int code = body.hasNonNull("statusCode") ? body.get("statusCode").asInt() : response.status();
        if (response.status() >= 400 || (code != 200 && code != 201)) {
            String message = body.hasNonNull("errorMessage") ? body.get("errorMessage").asString() : response.body();
            throw new PayoutRejectedException("Finik " + step + " rejected (" + code + "): " + message
                    + (recipientCheck ? " — check the shop's payout requisite" : ""));
        }
        return body;
    }
}
