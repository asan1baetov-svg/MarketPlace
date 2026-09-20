package greenecomall.mlm.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.mlm.MlmErrors;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * MLM-аккаунт внутреннего клиента на стороне маркетплейса. Связь с внешней системой — через
 * {@code mlmUserId}; сам вход и проверку токена делает auth-service, аккаунт заводится по
 * {@code auth.MlmUserLinked}. Условие «покупка на X за Y» считает маркетплейс (он владеет заказами)
 * и сообщает результат внешнему MLM-бэку.
 */
@Entity
@Table(name = "mlm_accounts")
public class MlmAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "mlm_user_id", nullable = false, unique = true, updatable = false, length = 128)
    private String mlmUserId;

    @Column(name = "referral_code", length = 64)
    private String referralCode;

    @Column(name = "upline_mlm_user_id", length = 128)
    private String uplineMlmUserId;

    @Column(name = "tariff_id")
    private UUID tariffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false, length = 16)
    private AccessStatus accessStatus = AccessStatus.NONE;

    /** Статус во внешней системе как он пришёл (для диагностики расхождений). */
    @Column(name = "external_status", length = 32)
    private String externalStatus;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "access_paid_at")
    private Instant accessPaidAt;

    @Column(name = "activation_deadline")
    private Instant activationDeadline;

    @Column(name = "required_purchase_amount_minor", nullable = false)
    private long requiredPurchaseAmountMinor;

    @Column(name = "achieved_purchase_amount_minor", nullable = false)
    private long achievedPurchaseAmountMinor;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "last_reminder_days")
    private Integer lastReminderDays;

    @Column(name = "bonus_balance_minor", nullable = false)
    private long bonusBalanceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MlmAccount() {
    }

    public MlmAccount(UUID userId, String mlmUserId, String referralCode, String uplineMlmUserId, String currency) {
        this.userId = userId;
        this.mlmUserId = mlmUserId;
        this.referralCode = referralCode;
        this.uplineMlmUserId = uplineMlmUserId;
        this.currency = currency;
        this.accessStatus = AccessStatus.NONE;
    }

    /**
     * Оплачен доступ: стартует (или перезапускается после EXPIRED) окно активации.
     * Из MUST_PURCHASE/ACTIVE повторная оплата ничего не меняет — возвращает {@code false}.
     */
    public boolean startActivationWindow(MlmTariff tariff, Instant paidAt) {
        requireNotBlocked();
        if (accessStatus == AccessStatus.MUST_PURCHASE || accessStatus == AccessStatus.ACTIVE) {
            return false;
        }
        this.tariffId = tariff.getId();
        this.accessStatus = AccessStatus.MUST_PURCHASE;
        this.accessPaidAt = paidAt;
        this.activationDeadline = paidAt.plus(Duration.ofDays(tariff.getPurchaseWindowDays()));
        this.requiredPurchaseAmountMinor = tariff.getRequiredPurchaseAmountMinor();
        this.achievedPurchaseAmountMinor = 0L;
        this.lastReminderDays = null;
        this.currency = tariff.getCurrency();
        return true;
    }

    /**
     * Засчитывает покупку, сделанную в окне активации.
     *
     * @return {@code true}, если этой покупкой условие активации выполнено
     */
    public boolean countPurchase(long amountMinor, Instant purchasedAt) {
        if (accessStatus != AccessStatus.MUST_PURCHASE || purchasedAt.isAfter(activationDeadline)) {
            return false;
        }
        this.achievedPurchaseAmountMinor = Math.addExact(achievedPurchaseAmountMinor, amountMinor);
        if (achievedPurchaseAmountMinor >= requiredPurchaseAmountMinor) {
            this.accessStatus = AccessStatus.ACTIVE;
            this.activatedAt = purchasedAt;
            return true;
        }
        return false;
    }

    /** Покупка отменена/возвращена до активации — сумма вычитается; активированный аккаунт не откатываем. */
    public void reversePurchase(long amountMinor) {
        if (accessStatus == AccessStatus.MUST_PURCHASE) {
            this.achievedPurchaseAmountMinor = Math.max(achievedPurchaseAmountMinor - amountMinor, 0L);
        }
    }

    public boolean expireIfOverdue(Instant now) {
        if (accessStatus == AccessStatus.MUST_PURCHASE && now.isAfter(activationDeadline)) {
            this.accessStatus = AccessStatus.EXPIRED;
            return true;
        }
        return false;
    }

    public void recordReminder(int daysLeft) {
        this.lastReminderDays = daysLeft;
    }

    public void syncExternal(String externalStatus, boolean blocked) {
        this.externalStatus = externalStatus;
        this.blocked = blocked;
    }

    public void creditBonus(long amountMinor) {
        this.bonusBalanceMinor = Math.addExact(bonusBalanceMinor, amountMinor);
    }

    public void debitBonus(long amountMinor) {
        this.bonusBalanceMinor = Math.max(bonusBalanceMinor - amountMinor, 0L);
    }

    public long remainingMinor() {
        return Math.max(requiredPurchaseAmountMinor - achievedPurchaseAmountMinor, 0L);
    }

    private void requireNotBlocked() {
        if (blocked) {
            throw new DomainException(MlmErrors.ACCOUNT_BLOCKED, "mlm account " + mlmUserId + " is blocked");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
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

    public UUID getTariffId() {
        return tariffId;
    }

    public AccessStatus getAccessStatus() {
        return accessStatus;
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public Instant getAccessPaidAt() {
        return accessPaidAt;
    }

    public Instant getActivationDeadline() {
        return activationDeadline;
    }

    public long getRequiredPurchaseAmountMinor() {
        return requiredPurchaseAmountMinor;
    }

    public long getAchievedPurchaseAmountMinor() {
        return achievedPurchaseAmountMinor;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Integer getLastReminderDays() {
        return lastReminderDays;
    }

    public long getBonusBalanceMinor() {
        return bonusBalanceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
