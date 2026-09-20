package greenecomall.order.domain;

import greenecomall.common.domain.DomainException;
import greenecomall.order.OrderErrors;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromocodeTest {

    private static Promocode percent(int pct) {
        return new Promocode("SAVE" + pct, PromocodeType.PERCENT, BigDecimal.valueOf(pct),
                PromocodeFundedBy.PLATFORM,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), null);
    }

    @Test
    void percentDiscountIsRoundedDownToWholeUnit() {
        // 1234.5 тыйын -> 12 сом: сумма к оплате должна быть в целых сомах (Finik)
        assertThat(percent(10).computeDiscountMinor(12_345)).isEqualTo(1_200);
    }

    @Test
    void fixedDiscountIsCappedAtOrderAmount() {
        Promocode fixed = new Promocode("MINUS500", PromocodeType.FIXED, new BigDecimal("500"),
                PromocodeFundedBy.SHOP,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), null);
        assertThat(fixed.computeDiscountMinor(300)).isEqualTo(300);
        assertThat(fixed.computeDiscountMinor(900)).isEqualTo(500);
    }

    @Test
    void outsideValidityWindowIsRejected() {
        Promocode expired = new Promocode("OLD", PromocodeType.PERCENT, BigDecimal.TEN, PromocodeFundedBy.PLATFORM,
                Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().minus(5, ChronoUnit.DAYS), null);
        assertThatThrownBy(() -> expired.assertUsable(Instant.now()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(OrderErrors.PROMOCODE_INVALID);
    }

    @Test
    void usageLimitIsEnforced() {
        Promocode limited = new Promocode("ONCE", PromocodeType.PERCENT, BigDecimal.TEN, PromocodeFundedBy.PLATFORM,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1);
        limited.recordUse();
        assertThatThrownBy(() -> limited.assertUsable(Instant.now()))
                .isInstanceOf(DomainException.class);
    }
}
