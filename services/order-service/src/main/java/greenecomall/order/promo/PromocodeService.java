package greenecomall.order.promo;

import greenecomall.common.domain.DomainException;
import greenecomall.order.OrderErrors;
import greenecomall.order.domain.Promocode;
import greenecomall.order.domain.PromocodeFundedBy;
import greenecomall.order.domain.PromocodeType;
import greenecomall.order.repo.PromocodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Промокоды: валидация при checkout, учёт использования, админ-CRUD.
 * Влияние {@code funded_by} на расчёт распределения средств считает finance-service.
 */
@Service
public class PromocodeService {

    private final PromocodeRepository promocodes;
    private final Clock clock;

    public PromocodeService(PromocodeRepository promocodes, Clock clock) {
        this.promocodes = promocodes;
        this.clock = clock;
    }

    /** Резолвит промокод и считает скидку от базовой суммы; бросает при неприменимости. */
    @Transactional(readOnly = true)
    public Applied apply(String code, long baseAmountMinor) {
        Promocode promocode = promocodes.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new DomainException(OrderErrors.PROMOCODE_NOT_FOUND, "promocode not found: " + code));
        promocode.assertUsable(Instant.now(clock));
        long discount = promocode.computeDiscountMinor(baseAmountMinor);
        return new Applied(promocode.getId(), discount, promocode.getFundedBy());
    }

    @Transactional
    public void recordUse(UUID promocodeId) {
        promocodes.findById(promocodeId).ifPresent(Promocode::recordUse);
    }

    @Transactional(readOnly = true)
    public List<Promocode> list() {
        return promocodes.findAll();
    }

    @Transactional
    public UUID create(String code, PromocodeType type, BigDecimal value, PromocodeFundedBy fundedBy,
                       Instant validFrom, Instant validTo, Integer usageLimit) {
        if (promocodes.existsByCodeIgnoreCase(code)) {
            throw new DomainException(OrderErrors.PROMOCODE_CODE_TAKEN, "promocode code already exists: " + code);
        }
        return promocodes.save(new Promocode(code, type, value, fundedBy, validFrom, validTo, usageLimit)).getId();
    }

    @Transactional
    public void update(UUID id, BigDecimal value, Instant validFrom, Instant validTo, Integer usageLimit, boolean active) {
        Promocode promocode = promocodes.findById(id)
                .orElseThrow(() -> new DomainException(OrderErrors.PROMOCODE_NOT_FOUND, "promocode not found: " + id));
        promocode.update(value, validFrom, validTo, usageLimit, active);
    }

    public record Applied(UUID promocodeId, long discountMinor, PromocodeFundedBy fundedBy) {
    }
}
