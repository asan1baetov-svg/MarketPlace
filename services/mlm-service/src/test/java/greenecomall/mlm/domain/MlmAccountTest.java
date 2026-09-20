package greenecomall.mlm.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.mlm.MlmErrors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MlmAccountTest {

    private final Instant paidAt = Instant.parse("2026-09-01T10:00:00Z");

    private static MlmTariff tariff() {
        MlmTariff t = new MlmTariff("Базовый", null, 1_000_000, "KGS", 500_000, 30, true);
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }

    private MlmAccount accountInWindow() {
        MlmAccount a = new MlmAccount(UUID.randomUUID(), "U1", "REF1", null, "KGS");
        assertThat(a.startActivationWindow(tariff(), paidAt)).isTrue();
        return a;
    }

    @Test
    void accessPaymentOpensWindowWithDeadline() {
        MlmAccount a = accountInWindow();
        assertThat(a.getAccessStatus()).isEqualTo(AccessStatus.MUST_PURCHASE);
        assertThat(a.getActivationDeadline()).isEqualTo(paidAt.plus(Duration.ofDays(30)));
        assertThat(a.getRequiredPurchaseAmountMinor()).isEqualTo(500_000);
    }

    @Test
    void purchasesAccumulateUntilConditionMet() {
        MlmAccount a = accountInWindow();
        assertThat(a.countPurchase(300_000, paidAt.plus(Duration.ofDays(1)))).isFalse();
        assertThat(a.remainingMinor()).isEqualTo(200_000);
        assertThat(a.countPurchase(250_000, paidAt.plus(Duration.ofDays(2)))).isTrue();
        assertThat(a.getAccessStatus()).isEqualTo(AccessStatus.ACTIVE);
        assertThat(a.getActivatedAt()).isNotNull();
    }

    @Test
    void purchaseAfterDeadlineIsNotCounted() {
        MlmAccount a = accountInWindow();
        assertThat(a.countPurchase(600_000, paidAt.plus(Duration.ofDays(31)))).isFalse();
        assertThat(a.getAchievedPurchaseAmountMinor()).isZero();
    }

    @Test
    void overdueWindowExpiresAndRepaymentRestartsIt() {
        MlmAccount a = accountInWindow();
        assertThat(a.expireIfOverdue(paidAt.plus(Duration.ofDays(29)))).isFalse();
        assertThat(a.expireIfOverdue(paidAt.plus(Duration.ofDays(31)))).isTrue();
        assertThat(a.getAccessStatus()).isEqualTo(AccessStatus.EXPIRED);

        Instant repaid = paidAt.plus(Duration.ofDays(40));
        assertThat(a.startActivationWindow(tariff(), repaid)).isTrue();
        assertThat(a.getAccessStatus()).isEqualTo(AccessStatus.MUST_PURCHASE);
        assertThat(a.getAchievedPurchaseAmountMinor()).isZero();
        assertThat(a.getActivationDeadline()).isEqualTo(repaid.plus(Duration.ofDays(30)));
    }

    @Test
    void repeatedAccessPaymentDuringWindowIsNoop() {
        MlmAccount a = accountInWindow();
        Instant deadline = a.getActivationDeadline();
        assertThat(a.startActivationWindow(tariff(), paidAt.plus(Duration.ofDays(5)))).isFalse();
        assertThat(a.getActivationDeadline()).isEqualTo(deadline);
    }

    @Test
    void reversalReducesProgressOnlyBeforeActivation() {
        MlmAccount a = accountInWindow();
        a.countPurchase(300_000, paidAt);
        a.reversePurchase(300_000);
        assertThat(a.getAchievedPurchaseAmountMinor()).isZero();

        a.countPurchase(500_000, paidAt);
        a.reversePurchase(500_000);
        assertThat(a.getAccessStatus()).isEqualTo(AccessStatus.ACTIVE);
    }

    @Test
    void blockedAccountCannotStartWindow() {
        MlmAccount a = new MlmAccount(UUID.randomUUID(), "U2", null, null, "KGS");
        a.syncExternal("BLOCKED", true);
        assertThatThrownBy(() -> a.startActivationWindow(tariff(), paidAt))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(MlmErrors.ACCOUNT_BLOCKED);
    }
}
