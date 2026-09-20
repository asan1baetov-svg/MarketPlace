package greenecomall.catalog.text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Чистит название и описание товара перед сохранением: механически ({@link TextNormalizer}) и
 * ИИ-корректурой ({@link TextProofreader}). Правка ИИ принимается, только если текст изменился
 * «как при исправлении ошибок», а не переписан: иначе остаётся текст магазина. Сбой ИИ не мешает
 * сохранить товар.
 */
@Service
public class ListingTextService {

    private static final Logger log = LoggerFactory.getLogger(ListingTextService.class);
    /** Доля символов, которую корректура может изменить (опечатки, запятые) — больше уже переписывание. */
    static final double MAX_CHANGE_RATIO = 0.15;

    private final TextProofreader proofreader;

    public ListingTextService(TextProofreader proofreader) {
        this.proofreader = proofreader;
    }

    public Result clean(String name, String description) {
        String cleanName = clean(name, TextProofreader.Field.NAME);
        String cleanDescription = clean(description, TextProofreader.Field.DESCRIPTION);
        boolean changed = !Objects.equals(cleanName, name) || !Objects.equals(cleanDescription, description);
        return new Result(cleanName, cleanDescription, changed);
    }

    String clean(String text, TextProofreader.Field field) {
        if (text == null || text.isBlank()) {
            return text == null ? null : "";
        }
        String normalized = TextNormalizer.normalize(text);
        String corrected;
        try {
            corrected = TextNormalizer.normalize(proofreader.proofread(normalized, field));
        } catch (RuntimeException e) {
            log.warn("proofreading unavailable, keeping normalized text: {}", e.toString());
            return normalized;
        }
        if (field == TextProofreader.Field.NAME && corrected.endsWith(".") && !normalized.endsWith(".")) {
            corrected = corrected.substring(0, corrected.length() - 1);
        }
        if (!isCorrectionOnly(normalized, corrected)) {
            log.info("proofreading rejected: model changed too much ({} -> {} chars)", normalized.length(), corrected.length());
            return normalized;
        }
        return corrected;
    }

    /** Корректура, а не переписывание: изменено не больше {@link #MAX_CHANGE_RATIO} символов. */
    static boolean isCorrectionOnly(String before, String after) {
        if (after.isBlank()) {
            return false;
        }
        int allowed = Math.max(3, (int) Math.ceil(before.length() * MAX_CHANGE_RATIO));
        if (Math.abs(before.length() - after.length()) > allowed) {
            return false;
        }
        return levenshtein(before, after, allowed) <= allowed;
    }

    /** Расстояние Левенштейна с ранним выходом, как только превышен {@code limit}. */
    static int levenshtein(String a, String b, int limit) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > limit) {
                return rowMin;
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    public record Result(String name, String description, boolean changed) {
    }
}
