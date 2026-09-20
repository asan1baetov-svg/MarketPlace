package greenecomall.courier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param offerTtl сколько курьер думает над оффером, прежде чем он уйдёт следующему кандидату
 * @param deliveryFeeMinor фиксированное вознаграждение за доставленный suborder (MVP — сдельно от платформы)
 * @param currency валюта вознаграждения
 */
@ConfigurationProperties(prefix = "courier")
public record CourierProperties(Duration offerTtl, long deliveryFeeMinor, String currency) {

    public CourierProperties {
        if (offerTtl == null) {
            offerTtl = Duration.ofMinutes(2);
        }
        if (currency == null || currency.isBlank()) {
            currency = "KGS";
        }
    }
}
