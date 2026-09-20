package greenecomall.finance.mlm;

import greenecomall.common.domain.DomainException;
import greenecomall.finance.FinanceErrors;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Цена доступа к MLM-программе из mlm-service ({@code GET /internal/mlm/tariffs/{id}}). Сумма платежа
 * за доступ берётся только отсюда — клиент не может сам назначить себе цену.
 */
@Component
public class MlmTariffClient {

    private final RestClient http;

    public MlmTariffClient(RestClient mlmRestClient) {
        this.http = mlmRestClient;
    }

    public Tariff payableTariff(UUID tariffId) {
        try {
            return http.get()
                    .uri("/internal/mlm/tariffs/{id}", tariffId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().is4xxClientError()) {
                            throw new DomainException(FinanceErrors.MLM_TARIFF_NOT_FOUND,
                                    "mlm tariff not payable: " + tariffId);
                        }
                        throw new DomainException(FinanceErrors.MLM_UNAVAILABLE,
                                "mlm tariff lookup failed: HTTP " + res.getStatusCode());
                    })
                    .body(Tariff.class);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException(FinanceErrors.MLM_UNAVAILABLE, "mlm tariff lookup failed for " + tariffId, e);
        }
    }

    public record Tariff(UUID id, long accessPriceMinor, String currency) {
    }
}
