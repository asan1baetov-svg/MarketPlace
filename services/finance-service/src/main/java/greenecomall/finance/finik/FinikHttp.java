package greenecomall.finance.finik;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Подписанный POST в API Finik. Редиректы не следуем: создание платежа отвечает {@code 302}
 * со ссылкой на оплату в {@code Location}. Подписывается ровно та строка JSON, что уходит в теле.
 */
class FinikHttp {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Clock clock;

    FinikHttp(Clock clock) {
        this.clock = clock;
    }

    Response post(String baseUrl, String path, String apiKey, PrivateKey key, String jsonBody) {
        URI uri = URI.create(baseUrl.replaceAll("/+$", "") + path);
        String timestamp = String.valueOf(clock.millis());
        String signature = FinikSigner.sign(FinikSigner.canonical("POST", path, uri.getHost(),
                Map.of("x-api-key", apiKey, "x-api-timestamp", timestamp), jsonBody), key);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("x-api-timestamp", timestamp)
                .header("signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers().firstValue("Location"), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FinikException("Finik call interrupted: " + path, e);
        } catch (Exception e) {
            throw new FinikException("Finik call failed: " + path, e);
        }
    }

    record Response(int status, Optional<String> location, String body) {
    }
}
