package greenecomall.catalog.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Фото товаров (префикс {@code catalog.photo}).
 *
 * @param storageDir    каталог файлов для {@link LocalImageStorage} (dev / один сервер)
 * @param publicBaseUrl публичный адрес раздачи файлов (через api-gateway), к нему дописывается ключ
 * @param maxUploadMb   лимит размера загружаемого фото
 * @param ai            генерация студийного фото и сцены из оригинала
 */
@ConfigurationProperties(prefix = "catalog.photo")
public record PhotoProperties(Path storageDir, String publicBaseUrl, Integer maxUploadMb, Ai ai) {

    public PhotoProperties {
        if (storageDir == null) {
            storageDir = Path.of("data", "media");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "http://localhost:8080/api/catalog/media";
        }
        if (maxUploadMb == null) {
            maxUploadMb = 10;
        }
        if (ai == null) {
            ai = new Ai(null, null, null, null);
        }
    }

    /**
     * @param provider {@code mock} (возвращает оригинал — для dev) | {@code gemini}
     * @param enabled  выключить генерацию целиком (тогда публикуется только оригинал)
     */
    public record Ai(String provider, Boolean enabled, String geminiApiKey, String geminiModel) {
        public Ai {
            if (provider == null || provider.isBlank()) {
                provider = "mock";
            }
            if (enabled == null) {
                enabled = true;
            }
            if (geminiModel == null || geminiModel.isBlank()) {
                geminiModel = "gemini-2.5-flash-image";
            }
        }
    }
}
