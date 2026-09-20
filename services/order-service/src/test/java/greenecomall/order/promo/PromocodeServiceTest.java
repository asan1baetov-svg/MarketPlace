package greenecomall.order.promo;

import greenecomall.common.domain.DomainException;
import greenecomall.order.OrderErrors;
import greenecomall.order.domain.Promocode;
import greenecomall.order.domain.PromocodeFundedBy;
import greenecomall.order.domain.PromocodeType;
import greenecomall.order.repo.PromocodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromocodeServiceTest {

    @Mock
    private PromocodeRepository promocodes;

    private PromocodeService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new PromocodeService(promocodes, clock);
    }

    @Test
    void apply_resolvesDiscountForValidCode() {
        Promocode code = new Promocode("SUMMER", PromocodeType.PERCENT, BigDecimal.valueOf(15),
                PromocodeFundedBy.PLATFORM,
                clock.instant().minus(1, ChronoUnit.DAYS), clock.instant().plus(30, ChronoUnit.DAYS), null);
        when(promocodes.findByCodeIgnoreCase("SUMMER")).thenReturn(Optional.of(code));

        PromocodeService.Applied applied = service.apply("SUMMER", 20_000);

        assertThat(applied.discountMinor()).isEqualTo(3_000);
        assertThat(applied.fundedBy()).isEqualTo(PromocodeFundedBy.PLATFORM);
    }

    @Test
    void apply_unknownCode_isNotFound() {
        when(promocodes.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.apply("NOPE", 1_000))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(OrderErrors.PROMOCODE_NOT_FOUND);
    }

    @Test
    void create_duplicateCode_isRejected() {
        when(promocodes.existsByCodeIgnoreCase("DUP")).thenReturn(true);
        assertThatThrownBy(() -> service.create("DUP", PromocodeType.FIXED, BigDecimal.TEN,
                PromocodeFundedBy.SHOP, clock.instant(), clock.instant().plus(1, ChronoUnit.DAYS), null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(OrderErrors.PROMOCODE_CODE_TAKEN);
    }
}
