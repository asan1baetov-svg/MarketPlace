package greenecomall.catalog.photo;

/** Генерация фото товара из оригинала по промпту. Выбор реализации — {@code catalog.photo.ai.provider}. */
public interface ProductPhotoAi {

    /** @return новое изображение (обычно PNG) */
    Rendered render(byte[] original, String mimeType, String prompt);

    record Rendered(byte[] data, String mimeType) {
    }
}
