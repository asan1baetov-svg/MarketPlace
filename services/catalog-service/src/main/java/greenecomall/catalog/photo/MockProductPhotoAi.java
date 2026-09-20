package greenecomall.catalog.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Для dev без ключа: возвращает оригинал как есть и пишет промпт в лог. */
@Component
@ConditionalOnProperty(prefix = "catalog.photo.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockProductPhotoAi implements ProductPhotoAi {

    private static final Logger log = LoggerFactory.getLogger(MockProductPhotoAi.class);

    @Override
    public Rendered render(byte[] original, String mimeType, String prompt) {
        log.info("[mock photo ai] prompt:\n{}", prompt);
        return new Rendered(original, mimeType);
    }
}
