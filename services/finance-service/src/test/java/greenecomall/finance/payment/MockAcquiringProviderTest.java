package greenecomall.finance.payment;

import greenecomall.finance.config.FinanceProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAcquiringProviderTest {

    private final MockAcquiringProvider provider =
            new MockAcquiringProvider(new FinanceProperties(null, null, "s3cr3t", null, null));

    @Test
    void validSignatureIsAccepted() {
        String body = "{\"status\":\"succeeded\"}";
        String sig = MockAcquiringProvider.hmacSha256Hex("s3cr3t", body);
        assertThat(provider.verifySignature(sig, body)).isTrue();
    }

    @Test
    void tamperedBodyOrWrongSecretIsRejected() {
        String body = "{\"status\":\"succeeded\"}";
        String sig = MockAcquiringProvider.hmacSha256Hex("s3cr3t", body);
        assertThat(provider.verifySignature(sig, "{\"status\":\"failed\"}")).isFalse();
        assertThat(provider.verifySignature(MockAcquiringProvider.hmacSha256Hex("other", body), body)).isFalse();
        assertThat(provider.verifySignature(null, body)).isFalse();
    }
}
