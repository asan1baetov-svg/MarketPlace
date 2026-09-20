package greenecomall.catalog.text;

/**
 * ИИ-корректор названия и описания товара: только орфография, грамматика, пунктуация, регистр,
 * абзацы — без перефразирования. Выбор реализации — {@code catalog.proofread.provider}.
 */
public interface TextProofreader {

    enum Field {NAME, DESCRIPTION}

    String proofread(String text, Field field);
}
