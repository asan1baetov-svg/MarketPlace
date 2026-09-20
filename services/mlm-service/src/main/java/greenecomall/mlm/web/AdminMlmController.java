package greenecomall.mlm.web;

import greenecomall.common.domain.DomainException;
import greenecomall.common.web.PageResponse;
import greenecomall.mlm.MlmErrors;
import greenecomall.mlm.domain.AccessStatus;
import greenecomall.mlm.domain.MlmAccount;
import greenecomall.mlm.domain.MlmReferralRate;
import greenecomall.mlm.domain.MlmSyncMessage;
import greenecomall.mlm.domain.MlmTariff;
import greenecomall.mlm.repo.MlmAccountRepository;
import greenecomall.mlm.repo.MlmOrderRepository;
import greenecomall.mlm.repo.MlmReferralRateRepository;
import greenecomall.mlm.repo.MlmSyncMessageRepository;
import greenecomall.mlm.repo.MlmTariffRepository;
import greenecomall.mlm.web.dto.MlmDtos.AccountResponse;
import greenecomall.mlm.web.dto.MlmDtos.RateRequest;
import greenecomall.mlm.web.dto.MlmDtos.RateResponse;
import greenecomall.mlm.web.dto.MlmDtos.SummaryResponse;
import greenecomall.mlm.web.dto.MlmDtos.SyncMessageResponse;
import greenecomall.mlm.web.dto.MlmDtos.TariffRequest;
import greenecomall.mlm.web.dto.MlmDtos.TariffResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Админ: тарифы доступа, реферальные проценты, список аккаунтов, отчёт, разбор сбоев синхронизации. */
@RestController
@RequestMapping("/admin/mlm")
public class AdminMlmController {

    private final MlmTariffRepository tariffs;
    private final MlmReferralRateRepository rates;
    private final MlmAccountRepository accounts;
    private final MlmOrderRepository orders;
    private final MlmSyncMessageRepository syncMessages;
    private final Clock clock;

    public AdminMlmController(MlmTariffRepository tariffs, MlmReferralRateRepository rates,
                              MlmAccountRepository accounts, MlmOrderRepository orders,
                              MlmSyncMessageRepository syncMessages, Clock clock) {
        this.tariffs = tariffs;
        this.rates = rates;
        this.accounts = accounts;
        this.orders = orders;
        this.syncMessages = syncMessages;
        this.clock = clock;
    }

    @GetMapping("/tariffs")
    public List<TariffResponse> tariffs() {
        return tariffs.findAll().stream().map(TariffResponse::from).toList();
    }

    @PostMapping("/tariffs")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TariffResponse createTariff(@Valid @RequestBody TariffRequest r) {
        if (r.isDefault()) {
            tariffs.findFirstByIsDefaultTrueAndActiveTrue().ifPresent(existing -> {
                throw new DomainException(MlmErrors.TARIFF_CONFLICT,
                        "default tariff already exists: " + existing.getId());
            });
        }
        return TariffResponse.from(tariffs.save(new MlmTariff(r.name(), r.countryId(), r.accessPriceMinor(),
                r.currency(), r.requiredPurchaseAmountMinor(), r.purchaseWindowDays(), r.isDefault())));
    }

    /** Изменение тарифа не меняет уже открытые окна активации — они живут по снимку на момент оплаты. */
    @PutMapping("/tariffs/{id}")
    @Transactional
    public TariffResponse updateTariff(@PathVariable UUID id, @Valid @RequestBody TariffRequest r) {
        MlmTariff tariff = tariffs.findById(id)
                .orElseThrow(() -> new DomainException(MlmErrors.TARIFF_NOT_FOUND, "tariff not found: " + id));
        tariff.update(r.name(), r.accessPriceMinor(), r.requiredPurchaseAmountMinor(), r.purchaseWindowDays(), r.active());
        return TariffResponse.from(tariff);
    }

    @GetMapping("/tariffs/{id}/referral-rates")
    public List<RateResponse> rates(@PathVariable UUID id) {
        return rates.findByTariffIdOrderByLevelAsc(id).stream()
                .map(rate -> new RateResponse(rate.getLevel(), rate.getPercent())).toList();
    }

    @PutMapping("/tariffs/{id}/referral-rates")
    @Transactional
    public List<RateResponse> putRates(@PathVariable UUID id, @Valid @RequestBody List<RateRequest> request) {
        if (!tariffs.existsById(id)) {
            throw new DomainException(MlmErrors.TARIFF_NOT_FOUND, "tariff not found: " + id);
        }
        for (RateRequest r : request) {
            rates.findByTariffIdAndLevel(id, r.level()).ifPresentOrElse(
                    existing -> existing.setPercent(r.percent()),
                    () -> rates.save(new MlmReferralRate(id, r.level(), r.percent())));
        }
        return rates(id);
    }

    @GetMapping("/accounts")
    public PageResponse<AccountResponse> accounts(@RequestParam(required = false) AccessStatus status, Pageable pageable) {
        Page<MlmAccount> page = status == null
                ? accounts.findAllByOrderByCreatedAtDesc(pageable)
                : accounts.findByAccessStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.of(page.getContent().stream().map(a -> AccountResponse.from(a, clock.instant())).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Активные/неактивные MLM-клиенты и выручка от них (дашборд, ТЗ §4.4). */
    @GetMapping("/reports/summary")
    public SummaryResponse summary() {
        return new SummaryResponse(accounts.count(),
                accounts.countByAccessStatus(AccessStatus.NONE),
                accounts.countByAccessStatus(AccessStatus.MUST_PURCHASE),
                accounts.countByAccessStatus(AccessStatus.ACTIVE),
                accounts.countByAccessStatus(AccessStatus.EXPIRED),
                orders.sumPaidRevenue());
    }

    @GetMapping("/sync/failed")
    public PageResponse<SyncMessageResponse> failedSync(Pageable pageable) {
        Page<MlmSyncMessage> page = syncMessages.findByStatusOrderByCreatedAtDesc(MlmSyncMessage.Status.FAILED, pageable);
        return PageResponse.of(page.getContent().stream().map(SyncMessageResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @PostMapping("/sync/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void retrySync(@PathVariable UUID id) {
        syncMessages.findById(id).ifPresent(m -> m.retryNow(clock.instant()));
    }
}
