package greenecomall.catalog.text;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ListingTextTest {

    @Test
    void normalizer_fixesSpacesPunctuationAndParagraphs() {
        String raw = "  Ваза  керамическая ,ручная работа.Высота 25 см ,цвет-бежевый  \n\n\n\nПодходит для сухоцветов !!  ";
        assertThat(TextNormalizer.normalize(raw)).isEqualTo(
                "Ваза керамическая, ручная работа. Высота 25 см, цвет-бежевый\n\nПодходит для сухоцветов!");
    }

    @Test
    void normalizer_keepsNumbersTimeAndUrls() {
        String raw = "Размер 1.5 м, вес 10,5 кг, доставка с 10:30, сайт example.kg/shop";
        assertThat(TextNormalizer.normalize(raw)).isEqualTo(raw);
    }

    @Test
    void aiCorrections_areAccepted() {
        ListingTextService service = new ListingTextService((text, field) ->
                text.replace("керамичиская", "керамическая").replace("работы", "работы,"));
        assertThat(service.clean("Ваза керамичиская", TextProofreader.Field.NAME)).isEqualTo("Ваза керамическая");
    }

    @Test
    void aiRewrite_isRejectedAndShopTextKept() {
        ListingTextService service = new ListingTextService((text, field) ->
                "Изысканная ваза ручной работы — идеальный акцент для вашего интерьера и отличный подарок");
        assertThat(service.clean("ваза для цветов белая", TextProofreader.Field.NAME)).isEqualTo("ваза для цветов белая");
    }

    @Test
    void aiFailure_doesNotBlockSaving() {
        ListingTextService service = new ListingTextService((text, field) -> {
            throw new IllegalStateException("Gemini down");
        });
        ListingTextService.Result r = service.clean("Сумка  кожаная", "Натуральная кожа ,молния.");
        assertThat(r.name()).isEqualTo("Сумка кожаная");
        assertThat(r.description()).isEqualTo("Натуральная кожа, молния.");
        assertThat(r.changed()).isTrue();
    }

    @Test
    void name_getsNoTrailingPeriodFromAi() {
        ListingTextService service = new ListingTextService((text, field) -> text + ".");
        assertThat(service.clean("Плед шерстяной", TextProofreader.Field.NAME)).isEqualTo("Плед шерстяной");
    }

    @Test
    void gemini_sendsStrictInstructionAndReadsText() throws Exception {
        AtomicReference<String> sent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = """
                    {"candidates":[{"content":{"parts":[{"text":"Ваза керамическая"}]}}]}"""
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        try {
            GeminiTextProofreader gemini = new GeminiTextProofreader(new ProofreadProperties("gemini", "k", null),
                    JsonMapper.builder().build(), "http://localhost:" + server.getAddress().getPort());

            assertThat(gemini.proofread("Ваза керамичиская", TextProofreader.Field.NAME)).isEqualTo("Ваза керамическая");

            JsonNode body = JsonMapper.builder().build().readTree(sent.get());
            assertThat(body.path("systemInstruction").toString()).contains("Do not rephrase");
            assertThat(body.path("generationConfig").path("temperature").asInt()).isZero();
            assertThat(body.path("contents").path(0).path("parts").path(0).path("text").asString())
                    .isEqualTo("Ваза керамичиская");
        } finally {
            server.stop(0);
        }
    }
}
