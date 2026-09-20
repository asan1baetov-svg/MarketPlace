package greenecomall.catalog.text;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Механическая чистка текста без ИИ: пробелы, пробелы вокруг знаков препинания, пустые строки.
 * Числа ({@code 1.5}, {@code 10,5}), время ({@code 10:30}) и адреса сайтов не трогаются.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String unified = text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ').replace('\t', ' ');
        String lines = Arrays.stream(unified.split("\n", -1))
                .map(TextNormalizer::normalizeLine)
                .collect(Collectors.joining("\n"));
        // не больше одной пустой строки между абзацами
        return lines.replaceAll("\n{3,}", "\n\n").strip();
    }

    private static String normalizeLine(String line) {
        String s = line.replaceAll(" {2,}", " ").strip();
        // без пробела перед знаком препинания: «слово ,» → «слово,»
        s = s.replaceAll(" +([,.;:!?…)])", "$1");
        s = s.replaceAll("([(«]) +", "$1");
        s = s.replaceAll(" +»", "»");
        // пробел после знака перед буквой: «слово,слово» → «слово, слово» (цифры и адреса не трогаем)
        s = s.replaceAll("([,;!?…])(?=[\\p{L}«(])", "$1 ");
        s = s.replaceAll("(?<=\\p{Ll})([.:])(?=\\p{Lu})", "$1 ");
        // повтор знаков: «,,» → «,», но «...» оставляем
        s = s.replaceAll(",{2,}", ",").replaceAll("!{2,}", "!").replaceAll("\\?{2,}", "?");
        return s;
    }
}
