package greenecomall.finance.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.finance.FinanceErrors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    @Test
    void creditAndDebitMoveBalance() {
        Wallet w = new Wallet(WalletOwnerType.SHOP, "shop-1", "KGS");
        assertThat(w.applyCredit(10_000)).isEqualTo(10_000);
        assertThat(w.applyDebit(3_000)).isEqualTo(7_000);
    }

    @Test
    void heldFundsAreNotAvailableForDebit() {
        Wallet w = new Wallet(WalletOwnerType.SHOP, "shop-1", "KGS");
        w.applyCredit(10_000);
        w.hold(8_000);
        assertThat(w.available()).isEqualTo(2_000);
        assertThatThrownBy(() -> w.applyDebit(5_000))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(FinanceErrors.WALLET_INSUFFICIENT_FUNDS);
    }

    @Test
    void cannotHoldMoreThanAvailable() {
        Wallet w = new Wallet(WalletOwnerType.COURIER, "c-1", "KGS");
        w.applyCredit(1_000);
        assertThatThrownBy(() -> w.hold(1_001)).isInstanceOf(DomainException.class);
    }

    @Test
    void releaseHoldNeverGoesNegative() {
        Wallet w = new Wallet(WalletOwnerType.SHOP, "shop-1", "KGS");
        w.applyCredit(1_000);
        w.hold(500);
        w.releaseHold(900);
        assertThat(w.getHeldMinor()).isZero();
    }
}
