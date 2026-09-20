package greenecomall.mlm.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.security.Authz;
import greenecomall.mlm.core.AccountService;
import greenecomall.mlm.core.ReferralService;
import greenecomall.mlm.domain.MlmAccount;
import greenecomall.mlm.repo.MlmBonusTransactionRepository;
import greenecomall.mlm.repo.MlmOrderRepository;
import greenecomall.mlm.web.dto.MlmDtos.AccountResponse;
import greenecomall.mlm.web.dto.MlmDtos.BonusResponse;
import greenecomall.mlm.web.dto.MlmDtos.OrderResponse;
import greenecomall.mlm.web.dto.MlmDtos.ReferralResponse;
import greenecomall.mlm.web.dto.MlmDtos.TariffResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Личный кабинет внутреннего клиента (ТЗ §4.2) + служебное чтение аккаунта/тарифа для других сервисов.
 * Аккаунт ищется по {@code sub} проверенного access-JWT.
 */
@RestController
public class MlmController {

    private final AccountService accounts;
    private final ReferralService referrals;
    private final MlmOrderRepository orders;
    private final MlmBonusTransactionRepository bonuses;
    private final Clock clock;

    public MlmController(AccountService accounts, ReferralService referrals, MlmOrderRepository orders,
                         MlmBonusTransactionRepository bonuses, Clock clock) {
        this.accounts = accounts;
        this.referrals = referrals;
        this.orders = orders;
        this.bonuses = bonuses;
        this.clock = clock;
    }

    @GetMapping("/mlm/me")
    public AccountResponse me(AuthPrincipal principal) {
        return AccountResponse.from(accounts.requireByUserId(Authz.require(principal).userId()), clock.instant());
    }

    @GetMapping("/mlm/me/orders")
    public List<OrderResponse> myOrders(AuthPrincipal principal) {
        MlmAccount account = accounts.requireByUserId(Authz.require(principal).userId());
        return orders.findByMlmAccountIdOrderByCreatedAtDesc(account.getId()).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/mlm/me/bonuses")
    public List<BonusResponse> myBonuses(AuthPrincipal principal) {
        MlmAccount account = accounts.requireByUserId(Authz.require(principal).userId());
        return bonuses.findByMlmAccountIdOrderByCreatedAtDesc(account.getId()).stream().map(BonusResponse::from).toList();
    }

    @GetMapping("/mlm/me/referrals")
    public List<ReferralResponse> myReferrals(AuthPrincipal principal,
                                              @RequestParam(defaultValue = "3") int depth) {
        MlmAccount account = accounts.requireByUserId(Authz.require(principal).userId());
        return referrals.referrals(account.getId(), Math.min(Math.max(depth, 1), 10)).stream()
                .map(r -> new ReferralResponse(r.level(), r.account().getMlmUserId(),
                        r.account().getAccessStatus().name(), r.account().getCreatedAt()))
                .toList();
    }

    /** Цена доступа для finance-service: сумма платежа берётся отсюда, а не из запроса клиента. */
    @GetMapping("/internal/mlm/tariffs/{id}")
    public TariffResponse payableTariff(@PathVariable UUID id) {
        return TariffResponse.from(accounts.payableTariff(id));
    }

    @GetMapping("/internal/mlm/accounts/{mlmUserId}")
    public AccountResponse byMlmUserId(@PathVariable String mlmUserId) {
        return AccountResponse.from(accounts.requireByMlmUserId(mlmUserId), clock.instant());
    }
}
