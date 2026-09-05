package greenecomall.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Журнал использованных входящих SSO-токенов MLM. Наличие {@code jti} = токен уже предъявлялся,
 * повторное предъявление отклоняется (защита от replay).
 */
@Entity
@Table(name = "mlm_sso_token_log")
public class MlmSsoTokenLog {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(name = "mlm_user_id", nullable = false, length = 100)
    private String mlmUserId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected MlmSsoTokenLog() {
    }

    public MlmSsoTokenLog(String jti, String mlmUserId, Instant issuedAt) {
        this.jti = jti;
        this.mlmUserId = mlmUserId;
        this.issuedAt = issuedAt;
        this.consumedAt = Instant.now();
    }

    public String getJti() {
        return jti;
    }

    public String getMlmUserId() {
        return mlmUserId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
