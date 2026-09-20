package greenecomall.catalog.photo;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Файлы на локальном диске, раздаются {@code GET /catalog/media/**}. Ключи генерирует только
 * сервис ({@code products/<uuid>/<uuid>.<ext>}) — всё прочее отклоняется, выйти за каталог нельзя.
 */
@Component
public class LocalImageStorage implements ImageStorage {

    static final Pattern KEY = Pattern.compile("products/[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|png|webp)");

    private final Path root;
    private final String publicBaseUrl;

    public LocalImageStorage(PhotoProperties properties) {
        this.root = properties.storageDir().toAbsolutePath().normalize();
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
    }

    @Override
    public String put(String key, byte[] data, String contentType) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot store " + key, e);
        }
        return publicBaseUrl + "/" + key;
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + key, e);
        }
    }

    Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid media key");
        }
        return root.resolve(key).normalize();
    }
}
