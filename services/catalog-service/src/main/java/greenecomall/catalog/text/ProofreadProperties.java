package greenecomall.catalog.text;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param provider {@code mock} | {@code gemini} */
@ConfigurationProperties(prefix = "catalog.proofread")
public record ProofreadProperties(String provider, String geminiApiKey, String geminiModel) {

    public ProofreadProperties {
        if (provider == null || provider.isBlank()) {
            provider = "mock";
        }
        if (geminiModel == null || geminiModel.isBlank()) {
            geminiModel = "gemini-2.5-flash";
        }
    }
}
