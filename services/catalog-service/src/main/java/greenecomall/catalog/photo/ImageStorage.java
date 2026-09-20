package greenecomall.catalog.photo;

/** Хранилище файлов фото. Сейчас — локальный диск; для продакшена — S3-совместимое (R2/MinIO). */
public interface ImageStorage {

    /** Сохраняет файл и возвращает публичный URL. */
    String put(String key, byte[] data, String contentType);

    byte[] get(String key);
}
