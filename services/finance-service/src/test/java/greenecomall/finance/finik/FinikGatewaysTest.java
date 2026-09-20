package greenecomall.finance.finik;

import com.sun.net.httpserver.HttpServer;
import greenecomall.common.domain.DomainException;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.domain.PayoutRequisite;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.payment.PaymentProvider;
import greenecomall.finance.wallet.PayoutGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Finik-эквайринг и переводы против локального фейкового сервера Finik: подписи, формат тел, ответы. */
class FinikGatewaysTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
    private static final String WEBHOOK_URL = "https://api.greenecomall.kg/api/webhooks/acquiring/finik";

    private final KeyPair platformKeys = rsa();
    private final KeyPair finikKeys = rsa();

    private HttpServer server;
    private final List<Captured> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();

    @BeforeEach
    void startFakeFinik() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Captured(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("x-api-key"),
                    exchange.getRequestHeaders().getFirst("x-api-timestamp"),
                    exchange.getRequestHeaders().getFirst("signature"), body));
            Reply reply = replies.getOrDefault(exchange.getRequestURI().getPath(), new Reply(404, null, ""));
            if (reply.location() != null) {
                exchange.getResponseHeaders().add("Location", reply.location());
            }
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                exchange.getResponseBody().write(out);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    // ─── эквайринг ─────────────────────────────────────────────────────────

    @Test
    void initiate_sendsSignedQrRequestAndReturnsRedirectUrl() {
        replies.put("/v1/payment", new Reply(302, "https://pay.finik.kg/qr/abc", ""));
        Payment payment = payment(29_000);

        PaymentProvider.HostedPage page = acquiring().initiate(payment);

        assertThat(page.redirectUrl()).isEqualTo("https://pay.finik.kg/qr/abc");
        assertThat(page.providerPaymentId()).isEqualTo(payment.getId().toString());
        Captured req = requests.getFirst();
        JsonNode body = JSON.readTree(req.body());
        assertThat(body.get("Amount").asInt()).isEqualTo(290);
        assertThat(body.get("PaymentId").asString()).isEqualTo(payment.getId().toString());
        assertThat(body.get("Data").get("webhookUrl").asString()).isEqualTo(WEBHOOK_URL);
        assertThat(req.apiKey()).isEqualTo("acq-key");
        String canonical = FinikSigner.canonical("POST", "/v1/payment", "localhost",
                Map.of("x-api-key", "acq-key", "x-api-timestamp", req.timestamp()), req.body());
        assertThat(FinikSigner.verify(canonical, req.signature(), platformKeys.getPublic()))
                .as("Finik проверит подпись нашим публичным ключом").isTrue();
    }

    @Test
    void initiate_rejectsAmountWithTyiyn() {
        assertThatThrownBy(() -> acquiring().initiate(payment(29_050))).isInstanceOf(DomainException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void webhook_signedByFinik_isParsed() {
        String raw = """
                {"transactionId":"fin-tx-1","status":"SUCCEEDED","amount":290,"fields":{"paymentId":"p-1"}}""";
        HttpHeaders headers = finikWebhookHeaders(raw);

        PaymentProvider.WebhookEvent event = acquiring().parseWebhook(headers, raw);

        assertThat(event.signatureValid()).isTrue();
        assertThat(event.providerEventId()).isEqualTo("fin-tx-1");
        assertThat(event.providerPaymentId()).isEqualTo("p-1");
        assertThat(event.status()).isEqualTo("SUCCEEDED");
        assertThat(event.amountMinor()).isEqualTo(29_000L);
    }

    @Test
    void webhook_forgedOrTampered_isInvalid() {
        String raw = """
                {"transactionId":"fin-tx-1","status":"SUCCEEDED","amount":290,"fields":{"paymentId":"p-1"}}""";
        HttpHeaders headers = finikWebhookHeaders(raw);

        assertThat(acquiring().parseWebhook(headers, raw.replace("290", "1")).signatureValid()).isFalse();
        HttpHeaders unsigned = new HttpHeaders();
        unsigned.set("X-Api-Timestamp", "1");
        assertThat(acquiring().parseWebhook(unsigned, raw).signatureValid()).isFalse();
    }

    // ─── переводы магазинам ────────────────────────────────────────────────

    @Test
    void transfer_checksRecipientThenPays() {
        replies.put("/v2/recipient", new Reply(200, null, "{\"statusCode\":200}"));
        replies.put("/v2/payment", new Reply(200, null, "{\"id\":\"fin-pay-7\"}"));

        String id = transfer().transfer(requisite(FinikBank.OPTIMA_BANK), 20_000, "payout-1", "GreenEcoMall 1");

        assertThat(id).isEqualTo("fin-pay-7");
        assertThat(requests).extracting(Captured::path).containsExactly("/v2/recipient", "/v2/payment");
        JsonNode payment = JSON.readTree(requests.get(1).body());
        assertThat(payment.get("transactionId").asString()).isEqualTo("payout-1");
        assertThat(payment.get("service").get("id").asString()).isEqualTo(FinikBank.OPTIMA_BANK.serviceId());
        assertThat(payment.get("fields").get("total").asInt()).isEqualTo(200);
        assertThat(payment.get("fields").get("account.value.persacc").asString()).isEqualTo("996555123456");
        assertThat(requests.get(1).apiKey()).isEqualTo("tr-key");
    }

    @Test
    void transfer_toMbank_usesMbankFieldFormat() {
        replies.put("/v2/recipient", new Reply(200, null, "{}"));
        replies.put("/v2/payment", new Reply(201, null, "{\"transactionId\":\"t-9\"}"));

        transfer().transfer(requisite(FinikBank.MBANK), 5_000, "payout-2", "c");

        JsonNode fields = JSON.readTree(requests.get(1).body()).get("fields");
        assertThat(fields.get("account").asString()).isEqualTo("996555123456");
        assertThat(fields.get("amount").asInt()).isEqualTo(50);
        assertThat(fields.get("provider").asString()).isEqualTo("c2c.mbank.kg");
    }

    @Test
    void transfer_badRecipient_isFinalRejection() {
        replies.put("/v2/recipient", new Reply(400, null, "{\"statusCode\":400,\"errorMessage\":\"account not found\"}"));

        assertThatThrownBy(() -> transfer().transfer(requisite(FinikBank.KICB), 20_000, "payout-3", "c"))
                .isInstanceOf(PayoutGateway.PayoutRejectedException.class)
                .hasMessageContaining("account not found");
        assertThat(requests).hasSize(1);
    }

    @Test
    void transfer_finikDown_isTransient() {
        replies.put("/v2/recipient", new Reply(503, null, "maintenance"));

        assertThatThrownBy(() -> transfer().transfer(requisite(FinikBank.KICB), 20_000, "payout-4", "c"))
                .isInstanceOf(FinikException.class);
    }

    @Test
    void transfer_aboveBankLimit_isRejectedWithoutCallingFinik() {
        assertThatThrownBy(() -> transfer().transfer(requisite(FinikBank.ELCART), 2_000_000, "payout-5", "c"))
                .isInstanceOf(PayoutGateway.PayoutRejectedException.class);
        assertThat(requests).isEmpty();
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private FinikAcquiringProvider acquiring() {
        return new FinikAcquiringProvider(properties(), JSON, CLOCK);
    }

    private FinikTransferGateway transfer() {
        return new FinikTransferGateway(properties(), JSON, CLOCK);
    }

    private FinikProperties properties() {
        String base = "http://localhost:" + server.getAddress().getPort();
        return new FinikProperties(pem("PUBLIC KEY", finikKeys.getPublic().getEncoded()),
                new FinikProperties.Acquiring(base, "acq-key", "acc-1", pem("PRIVATE KEY", platformKeys.getPrivate().getEncoded()),
                        WEBHOOK_URL, "https://greenecomall.kg/paid", null, null),
                new FinikProperties.Transfer(base, "tr-key", "tr-acc", "tr-user",
                        pem("PRIVATE KEY", platformKeys.getPrivate().getEncoded())));
    }

    private HttpHeaders finikWebhookHeaders(String raw) {
        String ts = "1758283200000";
        String canonical = "post\n/api/webhooks/acquiring/finik\nhost:api.greenecomall.kg&x-api-timestamp:" + ts + "\n" + raw;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Timestamp", ts);
        headers.set("Signature", FinikSigner.sign(canonical, finikKeys.getPrivate()));
        return headers;
    }

    private static Payment payment(long amountMinor) {
        Payment payment = Payment.forOrder(UUID.randomUUID(), UUID.randomUUID(), amountMinor, "KGS", "finik");
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    private static PayoutRequisite requisite(FinikBank bank) {
        return new PayoutRequisite(WalletOwnerType.SHOP, "shop-1", bank, "996555123456", Instant.now());
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(der) + "\n-----END " + type + "-----\n";
    }

    private record Captured(String path, String apiKey, String timestamp, String signature, String body) {
    }

    private record Reply(int status, String location, String body) {
    }
}
