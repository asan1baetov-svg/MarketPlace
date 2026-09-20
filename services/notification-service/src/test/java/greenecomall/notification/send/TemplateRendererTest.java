package greenecomall.notification.send;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void substitutesKnownAndBlanksUnknownPlaceholders() {
        String out = TemplateRenderer.render("Заказ {{orderId}}: {{ status }} {{missing}}",
                Map.of("orderId", "42", "status", "PAID"));
        assertThat(out).isEqualTo("Заказ 42: PAID ");
    }

    @Test
    void replacementValuesWithRegexCharactersAreLiteral() {
        assertThat(TemplateRenderer.render("{{amount}}", Map.of("amount", "$5\\0"))).isEqualTo("$5\\0");
    }
}
