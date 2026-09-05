package greenecomall.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Связь учётной записи маркетплейса с пользователем внешней MLM-системы.
 * Первичный ключ общий с {@link User} (shared PK, one-to-one).
 * {@code mlmUserId} — внешний стабильный идентификатор, по нему ищем аккаунт при SSO-входе.
 */
@Entity
@Table(name = "mlm_sso_identities")
public class MlmSsoIdentity {

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "mlm_user_id", nullable = false, unique = true, length = 100)
    private String mlmUserId;

    @Column(name = "referral_code", length = 64)
    private String referralCode;

    @Column(name = "upline_mlm_user_id", length = 100)
    private String uplineMlmUserId;

    @Column(name = "last_access_status", length = 30)
    private String lastAccessStatus;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected MlmSsoIdentity() {
    }

    public MlmSsoIdentity(User user, String mlmUserId, String referralCode,
                          String uplineMlmUserId, String lastAccessStatus) {
        this.user = user;
        this.mlmUserId = mlmUserId;
        this.referralCode = referralCode;
        this.uplineMlmUserId = uplineMlmUserId;
        this.lastAccessStatus = lastAccessStatus;
    }

    /** Отметить факт входа: обновить статус доступа со стороны MLM и время последнего логина. */
    public void touchLogin(String accessStatus, Instant now) {
        this.lastAccessStatus = accessStatus;
        this.lastLoginAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getMlmUserId() {
        return mlmUserId;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public String getUplineMlmUserId() {
        return uplineMlmUserId;
    }

    public String getLastAccessStatus() {
        return lastAccessStatus;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
