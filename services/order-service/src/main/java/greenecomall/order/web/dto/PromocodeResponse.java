package greenecomall.order.web.dto;

import greenecomall.order.domain.Promocode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromocodeResponse(
        UUID id, String code, String type, BigDecimal value, String fundedBy,
        Instant validFrom, Instant validTo, Integer usageLimit, int usedCount, boolean active) {

    public static PromocodeResponse from(Promocode p) {
        return new PromocodeResponse(
                p.getId(), p.getCode(), p.getType().name(), p.getValue(), p.getFundedBy().name(),
                p.getValidFrom(), p.getValidTo(), p.getUsageLimit(), p.getUsedCount(), p.isActive());
    }
}
