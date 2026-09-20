package greenecomall.catalog.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Фоном генерирует варианты фото из очереди: вызов ИИ идёт вне транзакции БД. */
@Component
@ConditionalOnProperty(prefix = "catalog.photo.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiPhotoJob {

    private static final Logger log = LoggerFactory.getLogger(AiPhotoJob.class);
    private static final int BATCH = 5;

    private final ProductPhotoService photos;
    private final ProductPhotoAi ai;

    public AiPhotoJob(ProductPhotoService photos, ProductPhotoAi ai) {
        this.photos = photos;
        this.ai = ai;
    }

    @Scheduled(fixedDelayString = "${catalog.photo.ai.poll-interval:15s}")
    public void run() {
        for (UUID imageId : photos.pending(BATCH)) {
            process(imageId);
        }
    }

    void process(UUID imageId) {
        ProductPhotoService.Work work = photos.claim(imageId).orElse(null);
        if (work == null) {
            return;
        }
        try {
            byte[] source = photos.read(work.sourceKey());
            String mime = ImageType.byExtension(work.sourceKey()).map(ImageType::mimeType).orElse("image/jpeg");
            photos.complete(imageId, ai.render(source, mime, work.prompt()));
            log.info("AI {} photo ready for product {}", work.style(), work.productId());
        } catch (RuntimeException e) {
            log.warn("AI {} photo for product {} failed: {}", work.style(), work.productId(), e.toString());
            photos.fail(imageId, e.getMessage());
        }
    }
}
