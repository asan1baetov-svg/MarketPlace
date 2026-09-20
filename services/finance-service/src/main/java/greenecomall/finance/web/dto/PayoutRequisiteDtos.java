package greenecomall.finance.web.dto;

import greenecomall.finance.domain.PayoutRequisite;
import greenecomall.finance.domain.Refund;
import greenecomall.finance.finik.FinikBank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Реквизиты автовыплат, справочник банков, ручные возвраты. */
public final class PayoutRequisiteDtos {

    private PayoutRequisiteDtos() {
    }

    public record BankResponse(String code, String name, int minSom, int maxSom) {
        public static BankResponse from(FinikBank bank) {
            return new BankResponse(bank.name(), bank.displayName(), bank.minSom(), bank.maxSom());
        }
    }

    /** @param phone номер, привязанный к счёту в банке: 996XXXXXXXXX, +996…, 0XXX… */
    public record AddRequisiteRequest(@NotNull FinikBank bank, @NotBlank String phone) {
    }

    public record RequisiteResponse(UUID id, String ownerType, String ownerRef, String bank, String bankName,
                                    String phone, String status, Instant createdAt) {
        public static RequisiteResponse from(PayoutRequisite r) {
            return new RequisiteResponse(r.getId(), r.getOwnerType().name(), r.getOwnerRef(), r.getBank().name(),
                    r.getBank().displayName(), r.getPhone(), r.getStatus().name(), r.getCreatedAt());
        }
    }

    public record RefundResponse(UUID id, UUID paymentId, long amountMinor, String reason, String status,
                                 String providerRefundId, Instant createdAt) {
        public static RefundResponse from(Refund r) {
            return new RefundResponse(r.getId(), r.getPaymentId(), r.getAmountMinor(), r.getReason(),
                    r.getStatus().name(), r.getProviderRefundId(), r.getCreatedAt());
        }
    }

    /** @param reference номер операции возврата в кабинете провайдера */
    public record CompleteRefundRequest(@NotBlank String reference) {
    }
}
