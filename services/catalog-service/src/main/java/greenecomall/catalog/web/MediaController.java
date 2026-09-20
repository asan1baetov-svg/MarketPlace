package greenecomall.catalog.web;

import greenecomall.catalog.photo.ImageType;
import greenecomall.catalog.photo.LocalImageStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Раздача фото из {@link LocalImageStorage} (публично, через {@code /api/catalog/media/**}).
 * Имена файлов уникальны и не меняются — кэшируем надолго. С S3/R2 раздача идёт напрямую с CDN.
 */
@RestController
public class MediaController {

    private final LocalImageStorage storage;

    public MediaController(LocalImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/catalog/media/products/{productId}/{file}")
    public ResponseEntity<byte[]> get(@PathVariable UUID productId, @PathVariable String file) {
        String key = "products/" + productId + "/" + file;
        ImageType type = ImageType.byExtension(file).orElse(null);
        if (type == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] data;
        try {
            data = storage.get(key);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(data);
    }
}
