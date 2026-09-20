package greenecomall.mlm.integration;

import greenecomall.mlm.domain.MlmSyncMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationPrimitivesTest {

    @Test
    void hmacRoundTripAndTamperDetection() {
        String sig = HmacSigner.sign("secret", "1700000000", "{\"a\":1}");
        assertThat(HmacSigner.verify("secret", "1700000000", "{\"a\":1}", sig)).isTrue();
        assertThat(HmacSigner.verify("secret", "1700000001", "{\"a\":1}", sig)).isFalse();
        assertThat(HmacSigner.verify("secret", "1700000000", "{\"a\":2}", sig)).isFalse();
        assertThat(HmacSigner.verify("other", "1700000000", "{\"a\":1}", sig)).isFalse();
        assertThat(HmacSigner.verify("secret", "1700000000", "{\"a\":1}", null)).isFalse();
    }

    @Test
    void signatureMatchesVectorProducedByMockMlmPython() {
        // python3: hmac.new(secret, b'1700000000.' + body, sha256).hexdigest() — infra/mock-mlm/app.py sign_body
        assertThat(HmacSigner.sign("local-dev-mlm-shared-secret-change-me", "1700000000",
                "{\"mlmUserId\":\"U1\",\"status\":\"ACTIVE\"}"))
                .isEqualTo("e11624f82e3a8e63441b27687dbfd39f810d37c0dc4091bc1fbede733d52b911");
    }

    @Test
    void externalStatusesOfRealMlmBackendAndDefaultContractAreMapped() {
        // реальный GreenEcoMall: AccountStatus PENDING | ACTIVE | BLOCKED
        assertThat(ExternalStatusMapper.map("ACTIVE")).isEqualTo(ExternalStatusMapper.Meaning.ACCESS_PAID);
        assertThat(ExternalStatusMapper.map("PENDING")).isEqualTo(ExternalStatusMapper.Meaning.NOT_PAID);
        assertThat(ExternalStatusMapper.map("BLOCKED")).isEqualTo(ExternalStatusMapper.Meaning.BLOCKED);
        // дефолтный контракт / mock-mlm
        assertThat(ExternalStatusMapper.map("access_paid")).isEqualTo(ExternalStatusMapper.Meaning.ACCESS_PAID);
        assertThat(ExternalStatusMapper.map(null)).isEqualTo(ExternalStatusMapper.Meaning.UNKNOWN);
        assertThat(ExternalStatusMapper.map("weird")).isEqualTo(ExternalStatusMapper.Meaning.UNKNOWN);
    }

    @Test
    void syncRetryBacksOffExponentiallyThenFails() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        MlmSyncMessage m = new MlmSyncMessage(MlmSyncMessage.Type.PURCHASE_REPORTED, "{}", now);

        m.markFailed("503", now, Duration.ofSeconds(30), 3);
        assertThat(m.getNextAttemptAt()).isEqualTo(now.plusSeconds(30));
        m.markFailed("503", now, Duration.ofSeconds(30), 3);
        assertThat(m.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));
        assertThat(m.getStatus()).isEqualTo(MlmSyncMessage.Status.PENDING);

        m.markFailed("503", now, Duration.ofSeconds(30), 3);
        assertThat(m.getStatus()).isEqualTo(MlmSyncMessage.Status.FAILED);
        assertThat(m.getAttempts()).isEqualTo(3);
    }
}
