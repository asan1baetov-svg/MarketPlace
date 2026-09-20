package greenecomall.notification.send;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Подстановка {@code {{key}}} из payload; неизвестный ключ остаётся пустой строкой. */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, ?> values) {
        if (template == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = values == null ? null : values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
