package greenecomall.catalog.text;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Корректура через Gemini: температура 0, без «размышлений» — быстро и предсказуемо. */
@Component
@ConditionalOnProperty(prefix = "catalog.proofread", name = "provider", havingValue = "gemini")
public class GeminiTextProofreader implements TextProofreader {

    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    static final String INSTRUCTION = """
            You are a proofreader for product listings in an online store. The text is in Russian or Kyrgyz \
            (sometimes with English brand names). Fix ONLY: spelling mistakes, grammar agreement, punctuation \
            (commas, periods, dashes, quotes), capitalization, extra or missing spaces, and paragraph breaks.
            Strict rules:
            - Do not rephrase, shorten, expand, translate or "improve" the wording.
            - Do not add or remove information, emojis or marketing phrases.
            - Keep brand names, model numbers, sizes, units, numbers and codes exactly as written.
            - Keep the original language of every word.
            - If the text is already correct, return it unchanged.
            Return only the corrected text, without quotes, comments or explanations.""";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiTextProofreader(ProofreadProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, DEFAULT_BASE_URL);
    }

    GeminiTextProofreader(ProofreadProperties properties, ObjectMapper objectMapper, String baseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = properties.geminiApiKey();
        this.model = properties.geminiModel();
        this.baseUrl = baseUrl;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("catalog.proofread.gemini-api-key is required for provider=gemini");
        }
    }

    @Override
    public String proofread(String text, Field field) {
        String hint = field == Field.NAME
                ? "This is a product NAME (one line, no final period)."
                : "This is a product DESCRIPTION (keep its paragraphs and lists).";
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", INSTRUCTION + "\n" + hint))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", text)))),
                "generationConfig", Map.of("temperature", 0, "thinkingConfig", Map.of("thinkingBudget", 0)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(15))
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
            throw new IllegalStateException("Gemini HTTP " + response.statusCode());
        }
        StringBuilder out = new StringBuilder();
        for (JsonNode part : objectMapper.readTree(response.body()).path("candidates").path(0).path("content").path("parts")) {
            out.append(part.path("text").asString(""));
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("Gemini returned no text");
        }
        return out.toString();
    }
}
