package greenecomall.finance.web;

import greenecomall.common.web.PageResponse;
import greenecomall.finance.domain.PayoutRequest;
import greenecomall.finance.domain.PayoutRequisite;
import greenecomall.finance.domain.Refund;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.payment.PaymentService;
import greenecomall.finance.wallet.PayoutRequisiteService;
import greenecomall.finance.wallet.PayoutService;
import greenecomall.finance.wallet.WalletService;
import greenecomall.finance.web.dto.PayoutRequisiteDtos.CompleteRefundRequest;
import greenecomall.finance.web.dto.PayoutRequisiteDtos.RefundResponse;
import greenecomall.finance.web.dto.PayoutRequisiteDtos.RequisiteResponse;
import greenecomall.finance.web.dto.PayoutResponse;
import greenecomall.finance.web.dto.ReasonRequest;
import greenecomall.finance.web.dto.WalletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Function;

/**
 * Админ: кошельки, выплаты (ручные заявки и автовыплаты магазинам), реквизиты автовыплат,
 * ручные возвраты клиентам (у Finik нет API возврата — админ возвращает в кабинете и подтверждает здесь).
 */
@RestController
@RequestMapping("/admin")
public class AdminFinanceController {

    private final PayoutService payoutService;
    private final WalletService walletService;
    private final PayoutRequisiteService requisites;
    private final PaymentService paymentService;

    public AdminFinanceController(PayoutService payoutService, WalletService walletService,
                                  PayoutRequisiteService requisites, PaymentService paymentService) {
        this.payoutService = payoutService;
        this.walletService = walletService;
        this.requisites = requisites;
        this.paymentService = paymentService;
    }

    /** Кошелёк любого владельца, включая кошельки платформы ({@code PLATFORM}). */
    @GetMapping("/wallets")
    public WalletResponse wallet(@RequestParam WalletOwnerType ownerType, @RequestParam String ownerRef,
                                 @RequestParam String currency) {
        return WalletResponse.from(walletService.require(ownerType, ownerRef, currency));
    }

    // ─── выплаты ───────────────────────────────────────────────────────────

    /** {@code status}: REQUESTED — ручные заявки к подтверждению; FAILED / SENDING — автовыплаты к разбору. */
    @GetMapping("/payouts")
    public PageResponse<PayoutResponse> payouts(@RequestParam(defaultValue = "REQUESTED") PayoutRequest.Status status,
                                                Pageable pageable) {
        return page(payoutService.byStatus(status, pageable), PayoutResponse::from);
    }

    /** Ручная заявка или FAILED-автовыплата: деньги переведены вручную — списать с кошелька. */
    @PostMapping("/payouts/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest request) {
        payoutService.approve(id, request == null ? "admin" : request.reasonOrDefault("admin"));
    }

    /** Отклонить заявку / отменить FAILED-автовыплату: сумма возвращается в доступный остаток. */
    @PostMapping("/payouts/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest request) {
        payoutService.reject(id, request == null ? "admin" : request.reasonOrDefault("admin"));
    }

    /** Повторить FAILED-автовыплату (например, после исправления реквизитов). */
    @PostMapping("/payouts/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable UUID id) {
        payoutService.retry(id);
    }

    // ─── реквизиты автовыплат ────────────────────────────────────────────────

    @GetMapping("/requisites")
    public PageResponse<RequisiteResponse> requisites(
            @RequestParam(defaultValue = "PENDING") PayoutRequisite.Status status, Pageable pageable) {
        return page(requisites.byStatus(status, pageable), RequisiteResponse::from);
    }

    @PostMapping("/requisites/{id}/approve")
    public RequisiteResponse approveRequisite(@PathVariable UUID id) {
        return RequisiteResponse.from(requisites.approve(id));
    }

    @PostMapping("/requisites/{id}/block")
    public RequisiteResponse blockRequisite(@PathVariable UUID id) {
        return RequisiteResponse.from(requisites.block(id));
    }

    // ─── возвраты ────────────────────────────────────────────────────────

    @GetMapping("/refunds")
    public PageResponse<RefundResponse> refunds(@RequestParam(defaultValue = "REQUESTED") Refund.Status status,
                                                Pageable pageable) {
        return page(paymentService.refundsByStatus(status, pageable), RefundResponse::from);
    }

    @PostMapping("/refunds/{id}/complete")
    public RefundResponse completeRefund(@PathVariable UUID id, @Valid @RequestBody CompleteRefundRequest request) {
        return RefundResponse.from(paymentService.completeManualRefund(id, request.reference()));
    }

    private static <T, R> PageResponse<R> page(Page<T> page, Function<T, R> mapper) {
        return PageResponse.of(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
