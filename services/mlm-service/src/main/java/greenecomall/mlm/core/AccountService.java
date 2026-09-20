package greenecomall.mlm.core;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.AuthEvents;
import greenecomall.common.events.payload.MlmEvents;
import greenecomall.common.events.payload.NotificationCommands;
import greenecomall.common.events.payload.OrderEvents;
import greenecomall.mlm.MlmErrors;
import greenecomall.mlm.config.MlmProperties;
import greenecomall.mlm.domain.AccessStatus;
import greenecomall.mlm.domain.MlmAccount;
import greenecomall.mlm.domain.MlmOrder;
import greenecomall.mlm.domain.MlmTariff;
import greenecomall.mlm.integration.ExternalStatusMapper;
import greenecomall.mlm.integration.MlmSyncClient;
import greenecomall.mlm.integration.MlmSyncOutbox;
import greenecomall.mlm.repo.MlmAccountRepository;
import greenecomall.mlm.repo.MlmOrderRepository;
import greenecomall.mlm.repo.MlmTariffRepository;
import greenecomall.mlm.support.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Жизненный цикл MLM-аккаунта на стороне маркетплейса (ТЗ §4.2, docs/ARCHITECTURE.md §2.6):
 * заведение по SSO-связке, старт окна активации по оплате доступа, зачёт оплаченных заказов,
 * активация при достижении суммы X, просрочка по дедлайну, напоминания. Каждое значимое
 * изменение уходит событием в Kafka и сообщением в outbox обратной синхронизации.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String PRODUCER = "mlm-service";

    private final MlmAccountRepository accounts;
    private final MlmOrderRepository orders;
    private final MlmTariffRepository tariffs;
    private final ReferralService referrals;
    private final MlmSyncOutbox syncOutbox;
    private final DomainEventPublisher events;
    private final MlmProperties properties;
    private final Clock clock;

    public AccountService(MlmAccountRepository accounts, MlmOrderRepository orders, MlmTariffRepository tariffs,
                          ReferralService referrals, MlmSyncOutbox syncOutbox, DomainEventPublisher events,
                          MlmProperties properties, Clock clock) {
        this.accounts = accounts;
        this.orders = orders;
        this.tariffs = tariffs;
        this.referrals = referrals;
        this.syncOutbox = syncOutbox;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    // ─── заведение и статус из внешней системы ────────────────────────────────

    @Transactional
    public MlmAccount link(AuthEvents.MlmUserLinked event) {
        MlmAccount account = accounts.findByMlmUserId(event.mlmUserId()).orElseGet(() -> {
            String currency = tariffs.findFirstByIsDefaultTrueAndActiveTrue().map(MlmTariff::getCurrency).orElse("KGS");
            MlmAccount created = accounts.save(new MlmAccount(event.userId(), event.mlmUserId(),
                    event.referralCode(), event.uplineMlmUserId(), currency));
            referrals.attach(created);
            return created;
        });
        applyExternalStatus(account, event.accessStatus(), null);
        return account;
    }

    /** Статус из внешнего MLM-бэка (SSO-claim или входящий webhook). */
    @Transactional
    public void applyExternalStatus(String mlmUserId, String externalStatus, UUID tariffId) {
        MlmAccount account = requireByMlmUserId(mlmUserId);
        applyExternalStatus(account, externalStatus, tariffId);
    }

    private void applyExternalStatus(MlmAccount account, String externalStatus, UUID tariffId) {
        ExternalStatusMapper.Meaning meaning = ExternalStatusMapper.map(externalStatus);
        if (meaning == ExternalStatusMapper.Meaning.UNKNOWN) {
            return;
        }
        account.syncExternal(externalStatus, meaning == ExternalStatusMapper.Meaning.BLOCKED);
        if (meaning == ExternalStatusMapper.Meaning.ACCESS_PAID) {
            startWindow(account, tariffId, Instant.now(clock));
        }
    }

    /**
     * Оплата доступа прошла через эквайринг маркетплейса ({@code payments.MlmAccessPaid}).
     * Сумму сверяем с тарифом ещё раз: finance берёт цену из тарифа при создании платежа, но окно
     * не должно открываться по недоплате, даже если платёж создан в обход (тогда — ручной разбор/возврат).
     */
    @Transactional
    public void onAccessPaid(String mlmUserId, UUID tariffId, long amountMinor, String currency, Instant paidAt) {
        MlmTariff tariff = resolveTariff(tariffId);
        if (amountMinor < tariff.getAccessPriceMinor() || !tariff.getCurrency().equals(currency)) {
            log.warn("mlm access payment for {} does not match tariff {}: paid {} {}, price {} {} — window not opened",
                    mlmUserId, tariff.getId(), amountMinor, currency, tariff.getAccessPriceMinor(), tariff.getCurrency());
            return;
        }
        startWindow(requireByMlmUserId(mlmUserId), tariff.getId(), paidAt);
    }

    /** Активный тариф для оплаты доступа; {@code null} — тариф по умолчанию. */
    @Transactional(readOnly = true)
    public MlmTariff payableTariff(UUID tariffId) {
        MlmTariff tariff = resolveTariff(tariffId);
        if (!tariff.isActive()) {
            throw new DomainException(MlmErrors.TARIFF_NOT_FOUND, "tariff is not active: " + tariff.getId());
        }
        return tariff;
    }

    private void startWindow(MlmAccount account, UUID tariffId, Instant paidAt) {
        MlmTariff tariff = resolveTariff(tariffId);
        if (account.isBlocked() || !account.startActivationWindow(tariff, paidAt)) {
            return;
        }
        events.publish(Topics.MLM, account.getMlmUserId(),
                EventEnvelope.of(EventTypes.MLM_ACCESS_ACTIVATED, PRODUCER, Tracing.currentTraceId(),
                        new MlmEvents.MlmAccessActivated(account.getMlmUserId(), account.getActivationDeadline(),
                                account.getRequiredPurchaseAmountMinor(), account.getCurrency())));
    }

    // ─── покупки ──────────────────────────────────────────────────────────────

    @Transactional
    public void onOrderCreated(OrderEvents.OrderCreated event) {
        if (orders.existsById(event.orderId())) {
            return;
        }
        accounts.findByUserId(event.clientUserId()).ifPresent(account ->
                orders.save(new MlmOrder(event.orderId(), account.getId(), event.totalAmountMinor(), event.currency())));
    }

    @Transactional
    public void onOrderPaid(UUID orderId, Instant paidAt) {
        MlmOrder order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != MlmOrder.Status.CREATED) {
            return; // не MLM-клиент или уже обработан
        }
        MlmAccount account = accounts.findById(order.getMlmAccountId()).orElseThrow();
        boolean inWindow = account.getAccessStatus() == AccessStatus.MUST_PURCHASE
                && !paidAt.isAfter(account.getActivationDeadline());
        boolean conditionMet = account.countPurchase(order.getAmountMinor(), paidAt);
        order.markPaid(inWindow, paidAt);

        syncOutbox.enqueue(new MlmSyncClient.Purchase(account.getMlmUserId(), orderId, order.getAmountMinor(),
                order.getCurrency(), "PAID", paidAt));
        referrals.accrueForPurchase(account, order);

        if (inWindow) {
            events.publish(Topics.MLM, account.getMlmUserId(),
                    EventEnvelope.of(EventTypes.MLM_PURCHASE_COUNTED, PRODUCER, Tracing.currentTraceId(),
                            new MlmEvents.MlmPurchaseCounted(account.getMlmUserId(), orderId,
                                    account.getAchievedPurchaseAmountMinor(), account.getRequiredPurchaseAmountMinor(),
                                    account.getCurrency())));
        }
        if (conditionMet) {
            List<UUID> counted = orders.findByMlmAccountIdOrderByCreatedAtDesc(account.getId()).stream()
                    .filter(o -> o.getStatus() == MlmOrder.Status.COUNTED)
                    .map(MlmOrder::getOrderId)
                    .toList();
            events.publish(Topics.MLM, account.getMlmUserId(),
                    EventEnvelope.of(EventTypes.MLM_CONDITION_MET, PRODUCER, Tracing.currentTraceId(),
                            new MlmEvents.MlmConditionMet(account.getMlmUserId(),
                                    account.getAchievedPurchaseAmountMinor(), account.getCurrency(), counted)));
            events.publish(Topics.MLM, account.getMlmUserId(),
                    EventEnvelope.of(EventTypes.MLM_ACTIVATED, PRODUCER, Tracing.currentTraceId(),
                            new MlmEvents.MlmActivated(account.getMlmUserId(), account.getActivatedAt())));
            syncOutbox.enqueue(new MlmSyncClient.ActivationStatus(account.getMlmUserId(), "CONDITION_MET",
                    account.getAchievedPurchaseAmountMinor(), account.getRequiredPurchaseAmountMinor(),
                    account.getCurrency(), paidAt, counted));
            log.info("mlm account {} activated", account.getMlmUserId());
        }
    }

    /** Отмена или возврат заказа MLM-клиента. */
    @Transactional
    public void onOrderReversed(UUID orderId) {
        MlmOrder order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() == MlmOrder.Status.REVERSED) {
            return;
        }
        boolean wasPaid = order.isPaid();
        boolean wasCounted = order.getStatus() == MlmOrder.Status.COUNTED;
        order.markReversed();
        if (!wasPaid) {
            return;
        }
        MlmAccount account = accounts.findById(order.getMlmAccountId()).orElseThrow();
        if (wasCounted) {
            account.reversePurchase(order.getAmountMinor());
        }
        referrals.reverseForOrder(orderId);
        syncOutbox.enqueue(new MlmSyncClient.Purchase(account.getMlmUserId(), orderId, order.getAmountMinor(),
                order.getCurrency(), "REVERSED", Instant.now(clock)));
    }

    // ─── планировщик ───────────────────────────────────────────────────────────

    @Transactional
    public int expireOverdue() {
        Instant now = Instant.now(clock);
        int expired = 0;
        for (MlmAccount account : accounts.findByAccessStatus(AccessStatus.MUST_PURCHASE)) {
            if (account.expireIfOverdue(now)) {
                expired++;
                events.publish(Topics.MLM, account.getMlmUserId(),
                        EventEnvelope.of(EventTypes.MLM_EXPIRED, PRODUCER, Tracing.currentTraceId(),
                                new MlmEvents.MlmExpired(account.getMlmUserId(), account.getActivationDeadline())));
                syncOutbox.enqueue(new MlmSyncClient.ActivationStatus(account.getMlmUserId(), "EXPIRED",
                        account.getAchievedPurchaseAmountMinor(), account.getRequiredPurchaseAmountMinor(),
                        account.getCurrency(), now, List.of()));
            }
        }
        return expired;
    }

    /** Напоминание за N дней до дедлайна (по умолчанию за 3 и за 1) — одно на каждый порог. */
    @Transactional
    public int sendDeadlineReminders() {
        Instant now = Instant.now(clock);
        List<Integer> thresholds = properties.reminderDaysBeforeDeadline().stream().sorted().toList();
        int sent = 0;
        for (MlmAccount account : accounts.findByAccessStatus(AccessStatus.MUST_PURCHASE)) {
            long hoursLeft = Duration.between(now, account.getActivationDeadline()).toHours();
            if (hoursLeft < 0) {
                continue;
            }
            int daysLeft = (int) Math.ceil(hoursLeft / 24.0);
            Integer threshold = thresholds.stream().filter(t -> daysLeft <= t).findFirst().orElse(null);
            if (threshold == null || (account.getLastReminderDays() != null && account.getLastReminderDays() <= threshold)) {
                continue;
            }
            account.recordReminder(threshold);
            events.publish(Topics.NOTIFICATION_COMMANDS, account.getUserId().toString(),
                    EventEnvelope.of(EventTypes.SEND_NOTIFICATION, PRODUCER, Tracing.currentTraceId(),
                            new NotificationCommands.SendNotification(account.getUserId(), "MLM_DEADLINE_REMINDER", null,
                                    Map.of("daysLeft", daysLeft,
                                            "remaining", formatMoney(account.remainingMinor(), account.getCurrency())))));
            sent++;
        }
        return sent;
    }

    // ─── запросы ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MlmAccount requireByUserId(UUID userId) {
        return accounts.findByUserId(userId)
                .orElseThrow(() -> new DomainException(MlmErrors.ACCOUNT_NOT_FOUND, "no mlm account for user " + userId));
    }

    @Transactional(readOnly = true)
    public MlmAccount requireByMlmUserId(String mlmUserId) {
        return accounts.findByMlmUserId(mlmUserId)
                .orElseThrow(() -> new DomainException(MlmErrors.ACCOUNT_NOT_FOUND, "no mlm account " + mlmUserId));
    }

    private MlmTariff resolveTariff(UUID tariffId) {
        if (tariffId != null) {
            return tariffs.findById(tariffId)
                    .orElseThrow(() -> new DomainException(MlmErrors.TARIFF_NOT_FOUND, "tariff not found: " + tariffId));
        }
        return tariffs.findFirstByIsDefaultTrueAndActiveTrue()
                .orElseThrow(() -> new DomainException(MlmErrors.NO_DEFAULT_TARIFF, "no active default mlm tariff"));
    }

    private static String formatMoney(long minor, String currency) {
        return String.format("%d.%02d %s", minor / 100, minor % 100, currency);
    }
}
