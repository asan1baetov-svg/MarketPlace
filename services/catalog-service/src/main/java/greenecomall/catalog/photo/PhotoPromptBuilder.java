package greenecomall.catalog.photo;

import greenecomall.catalog.domain.PhotoStyle;
import greenecomall.catalog.domain.Product;

/**
 * Промпт для ИИ: что это за товар (название и описание, категория — подсказка), выбранный магазином
 * стиль кадра и его пожелание. Правила, которые пожелание не может отменить: сам товар не
 * перерисовывается (покупатель получает ровно то, что на фото), без надписей и водяных знаков,
 * люди — только в стилях с человеком и только взрослые.
 */
public final class PhotoPromptBuilder {

    private static final String KEEP_PRODUCT = """
            Keep the product itself exactly as in the source photo: same shape, proportions, colours, materials, \
            texture, logos and printed text. Do not redraw, restyle, add or remove any part of the product. \
            Square 1:1, crisp focus on the product, true-to-life colours, photorealistic. No added text, \
            no watermark, no borders.""";

    private static final String NO_PEOPLE = "No people, faces or hands in the frame.";

    private static final String PEOPLE_RULES = """
            Any person shown is a generic adult (never a child), not resembling any real or famous person, \
            dressed modestly and appropriately.""";

    private PhotoPromptBuilder() {
    }

    public static String build(PhotoStyle style, Product product, String wish) {
        StringBuilder prompt = new StringBuilder()
                .append("Professional photo for an online store of this product: ").append(describe(product)).append('\n')
                .append(style.scene()).append('\n');
        if (wish != null && !wish.isBlank()) {
            prompt.append("Seller's wish for this shot (follow it unless it conflicts with the rules below): ")
                    .append(clean(wish, 200)).append('\n');
        }
        prompt.append(KEEP_PRODUCT).append('\n')
                .append(style.withPeople() ? PEOPLE_RULES : NO_PEOPLE);
        return prompt.toString();
    }

    /** Название + описание (+ категория), без переводов строк и с ограничением длины. */
    static String describe(Product product) {
        StringBuilder sb = new StringBuilder("\"").append(clean(product.getName(), 200)).append("\"");
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            sb.append(". Description: ").append(clean(product.getDescription(), 600));
        }
        if (product.getCategory() != null) {
            sb.append(". Category: ").append(clean(product.getCategory().getName(), 100));
        }
        return sb.toString();
    }

    private static String clean(String text, int max) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
