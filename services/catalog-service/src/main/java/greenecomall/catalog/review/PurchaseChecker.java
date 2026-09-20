package greenecomall.catalog.review;

import greenecomall.catalog.CatalogErrors;
import greenecomall.common.domain.DomainException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/** Спрашивает order-service, доставлен ли товар этому покупателю ({@code /internal/purchases/received}). */
@Component
public class PurchaseChecker {

    private final RestClient http;

    public PurchaseChecker(RestClient orderRestClient) {
        this.http = orderRestClient;
    }

    public boolean hasReceived(UUID clientUserId, UUID productId) {
        try {
            Map<?, ?> body = http.get()
                    .uri(uri -> uri.path("/internal/purchases/received")
                            .queryParam("clientUserId", clientUserId)
                            .queryParam("productId", productId)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new DomainException(CatalogErrors.ORDER_UNAVAILABLE,
                                "purchase check failed: HTTP " + res.getStatusCode());
                    })
                    .body(Map.class);
            return body != null && Boolean.TRUE.equals(body.get("received"));
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException(CatalogErrors.ORDER_UNAVAILABLE, "purchase check failed", e);
        }
    }
}
