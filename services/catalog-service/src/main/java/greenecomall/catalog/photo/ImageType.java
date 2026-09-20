package greenecomall.catalog.photo;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Тип изображения по сигнатуре файла, а не по имени/заголовку — их легко подделать. */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    ImageType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public static Optional<ImageType> detect(byte[] data) {
        if (data == null || data.length < 12) {
            return Optional.empty();
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return Optional.of(PNG);
        }
        if ("RIFF".equals(new String(data, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(data, 8, 4, StandardCharsets.US_ASCII))) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static Optional<ImageType> byExtension(String key) {
        for (ImageType type : values()) {
            if (key.endsWith("." + type.extension)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }
}
