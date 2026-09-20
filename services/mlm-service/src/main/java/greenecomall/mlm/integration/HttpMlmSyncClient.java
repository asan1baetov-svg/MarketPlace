package greenecomall.mlm.integration;

import greenecomall.mlm.config.MlmProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * HTTP-реализация {@link MlmSyncClient}. Эндпоинты — предложенный контракт, которого во внешнем
 * MLM-бэке (GreenEcoMall, Spring Boot 3.3) пока нет; команда MLM должна добавить их
 * (docs/ARCHITECTURE.md §5.4). Ответ не-2xx → исключение → ретрай в {@code MlmSyncRelay}.
 */
@Component
public class HttpMlmSyncClient implements MlmSyncClient {

    static final String ACTIVATION_PATH = "/api/marketplace/activation";
    static final String PURCHASES_PATH = "/api/marketplace/purchases";

    private final RestClient http;
    private final MlmProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public HttpMlmSyncClient(RestClient mlmBackendRestClient, MlmProperties properties,
                             ObjectMapper objectMapper, Clock clock) {
        this.http = mlmBackendRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void reportActivationStatus(ActivationStatus status, String idempotencyKey) {
        post(ACTIVATION_PATH, status, idempotencyKey);
    }

    @Override
    public void reportPurchase(Purchase purchase, String idempotencyKey) {
        post(PURCHASES_PATH, purchase, idempotencyKey);
    }

    private void post(String path, Object payload, String idempotencyKey) {
        if (!properties.sync().enabled()) {
            throw new IllegalStateException("mlm.sync.base-url is not configured");
        }
        String body = objectMapper.writeValueAsString(payload);
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HmacSigner.TIMESTAMP_HEADER, timestamp)
                .header(HmacSigner.SIGNATURE_HEADER, HmacSigner.sign(properties.sync().sharedSecret(), timestamp, body))
                .header(HmacSigner.IDEMPOTENCY_HEADER, idempotencyKey)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
