package greenecomall.catalog.text;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Без ИИ (dev): текст не меняется, работает только {@link TextNormalizer}. */
@Component
@ConditionalOnProperty(prefix = "catalog.proofread", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockTextProofreader implements TextProofreader {

    @Override
    public String proofread(String text, Field field) {
        return text;
    }
}
