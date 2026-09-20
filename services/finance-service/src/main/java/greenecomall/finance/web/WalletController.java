package greenecomall.finance.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.PageResponse;
import greenecomall.common.web.security.Authz;
import greenecomall.finance.domain.PayoutRequest;
import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletTransaction;
import greenecomall.finance.finik.FinikBank;
import greenecomall.finance.wallet.PayoutRequisiteService;
import greenecomall.finance.wallet.PayoutService;
import greenecomall.finance.wallet.WalletService;
import greenecomall.finance.web.dto.CreatePayoutRequest;
import greenecomall.finance.web.dto.PayoutRequisiteDtos.AddRequisiteRequest;
import greenecomall.finance.web.dto.PayoutRequisiteDtos.BankResponse;
import greenecomall.finance.web.dto.PayoutRequisiteDtos.RequisiteResponse;
import greenecomall.finance.web.dto.PayoutResponse;
import greenecomall.finance.web.dto.WalletResponse;
import greenecomall.finance.web.dto.WalletTransactionResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Кошельки магазина/курьера пользователя из access-JWT: балансы, история проводок, заявка на вывод.
 * Владение — через {@code wallet_owners} (см. {@link WalletService#isOwnedBy}); админ видит любые.
 */
@RestController
public class WalletController {

    private final WalletService walletService;
    private final PayoutService payoutService;
    private final PayoutRequisiteService requisites;

    public WalletController(WalletService walletService, PayoutService payoutService,
                            PayoutRequisiteService requisites) {
        this.walletService = walletService;
        this.payoutService = payoutService;
        this.requisites = requisites;
    }

    /** Банки, на которые Finik переводит по номеру телефона, с лимитами одного перевода. */
    @GetMapping("/wallet/banks")
    public List<BankResponse> banks() {
        return Arrays.stream(FinikBank.values()).map(BankResponse::from).toList();
    }

    /** Реквизиты для автовыплат магазину — начнут работать после одобрения админом. */
    @PostMapping("/wallet/{walletId}/requisites")
    @ResponseStatus(HttpStatus.CREATED)
    public RequisiteResponse addRequisite(@PathVariable UUID walletId, @Valid @RequestBody AddRequisiteRequest request,
                                          AuthPrincipal principal) {
        Wallet wallet = requireOwnWallet(principal, walletId);
        return RequisiteResponse.from(requisites.add(wallet, request.bank(), request.phone()));
    }

    @GetMapping("/wallet/{walletId}/requisites")
    public List<RequisiteResponse> requisites(@PathVariable UUID walletId, AuthPrincipal principal) {
        Wallet wallet = requireOwnWallet(principal, walletId);
        return requisites.forOwner(wallet.getOwnerType(), wallet.getOwnerRef()).stream()
                .map(RequisiteResponse::from).toList();
    }

    /** История выплат кошелька, включая автовыплаты и их ошибки. */
    @GetMapping("/wallet/{walletId}/payouts")
    public PageResponse<PayoutResponse> payouts(@PathVariable UUID walletId, Pageable pageable, AuthPrincipal principal) {
        requireOwnWallet(principal, walletId);
        Page<PayoutRequest> page = payoutService.forWallet(walletId, pageable);
        return PageResponse.of(page.getContent().stream().map(PayoutResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @GetMapping("/wallet")
    public List<WalletResponse> mine(AuthPrincipal principal) {
        return walletService.ownedBy(Authz.require(principal).userId()).stream().map(WalletResponse::from).toList();
    }

    @GetMapping("/wallet/{walletId}/transactions")
    public PageResponse<WalletTransactionResponse> transactions(@PathVariable UUID walletId, Pageable pageable,
                                                               AuthPrincipal principal) {
        requireOwnWallet(principal, walletId);
        Page<WalletTransaction> page = walletService.history(walletId, pageable);
        return PageResponse.of(page.getContent().stream().map(WalletTransactionResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @PostMapping("/wallet/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutResponse requestPayout(@Valid @RequestBody CreatePayoutRequest request, AuthPrincipal principal) {
        requireOwnWallet(principal, request.walletId());
        PayoutRequest payout = payoutService.request(request.walletId(), request.amountMinor(),
                principal.userId().toString(), request.bankDetails().toString());
        return PayoutResponse.from(payout);
    }

    private Wallet requireOwnWallet(AuthPrincipal principal, UUID walletId) {
        Authz.require(principal);
        Wallet wallet = walletService.require(walletId);
        if (!Authz.isAdmin(principal) && !walletService.isOwnedBy(wallet, principal.userId())) {
            throw new AccessDeniedException("wallet belongs to another user");
        }
        return wallet;
    }
}
