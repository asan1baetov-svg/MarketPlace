package greenecomall.catalog.photo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Генерация через Google Gemini (редактирование изображения по промпту, {@code generateContent}):
 * на вход — оригинал магазина и промпт, на выход — изображение 1:1.
 */
@Component
@ConditionalOnProperty(prefix = "catalog.photo.ai", name = "provider", havingValue = "gemini")
public class GeminiProductPhotoAi implements ProductPhotoAi {

    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiProductPhotoAi(PhotoProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, DEFAULT_BASE_URL);
    }

    GeminiProductPhotoAi(PhotoProperties properties, ObjectMapper objectMapper, String baseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = properties.ai().geminiApiKey();
        this.model = properties.ai().geminiModel();
        this.baseUrl = baseUrl;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("catalog.photo.ai.gemini-api-key is required for provider=gemini");
        }
    }

    @Override
    public Rendered render(byte[] original, String mimeType, String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", prompt),
                        Map.of("inline_data", Map.of("mime_type", mimeType,
                                "data", Base64.getEncoder().encodeToString(original)))))),
                "generationConfig", Map.of(
                        "responseModalities", List.of("IMAGE"),
                        "imageConfig", Map.of("aspectRatio", "1:1")));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini call interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Gemini call failed: " + e.getMessage(), e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
        }
        JsonNode parts = objectMapper.readTree(response.body()).path("candidates").path(0).path("content").path("parts");
        for (JsonNode part : parts) {
            JsonNode inline = part.has("inlineData") ? part.get("inlineData") : part.get("inline_data");
            if (inline != null && inline.hasNonNull("data")) {
                String mime = inline.has("mimeType") ? inline.get("mimeType").asString()
                        : inline.path("mime_type").asString("image/png");
                return new Rendered(Base64.getDecoder().decode(inline.get("data").asString()), mime);
            }
        }
        throw new IllegalStateException("Gemini returned no image: " + abbreviate(response.body()));
    }

    private static String abbreviate(String s) {
        return s == null || s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
