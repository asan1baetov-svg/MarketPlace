package greenecomall.finance.wallet;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.WalletEvents;
import greenecomall.finance.FinanceErrors;
import greenecomall.finance.domain.PayoutRequest;
import greenecomall.finance.domain.PayoutRequisite;
import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletTransaction;
import greenecomall.finance.repo.PayoutRequestRepository;
import greenecomall.finance.repo.WalletRepository;
import greenecomall.finance.support.Tracing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Выплаты с кошельков.
 * <ul>
 *   <li>Ручные: магазин/курьер создаёт заявку (средства уходят в hold), админ подтверждает
 *       (hold → debit, перевод делается вручную) или отклоняет (hold освобождается).</li>
 *   <li>Автоматические (магазины): после оплаты заказа часть магазина ставится в очередь
 *       ({@link #queueAuto}), {@link AutoPayoutJob} переводит её через {@link PayoutGateway} на
 *       одобренные реквизиты. Сам перевод идёт вне транзакции БД: сначала SENDING, потом результат.</li>
 * </ul>
 */
@Service
public class PayoutService {

    private static final String PRODUCER = "finance-service";

    private static final Duration REQUISITE_WAIT = Duration.ofMinutes(10);

    private final PayoutRequestRepository payouts;
    private final WalletRepository wallets;
    private final WalletService walletService;
    private final PayoutRequisiteService requisites;
    private final DomainEventPublisher events;
    private final Clock clock;

    public PayoutService(PayoutRequestRepository payouts, WalletRepository wallets, WalletService walletService,
                         PayoutRequisiteService requisites, DomainEventPublisher events, Clock clock) {
        this.payouts = payouts;
        this.wallets = wallets;
        this.walletService = walletService;
        this.requisites = requisites;
        this.events = events;
        this.clock = clock;
    }

    // ─── автовыплаты ─────────────────────────────────────────────────────────

    /**
     * Ставит в очередь выплату магазину его части оплаченного заказа. Сумма — не больше доступного
     * остатка (долг после возвратов гасится из новых продаж) и округляется вниз до целого сома:
     * банки принимают только целые суммы, остаток копится на кошельке.
     */
    @Transactional
    public void queueAuto(UUID walletId, long shareMinor, String reference) {
        Wallet wallet = wallets.findWithLockById(walletId)
                .orElseThrow(() -> new DomainException(FinanceErrors.WALLET_NOT_FOUND, "wallet not found: " + walletId));
        long amount = Math.min(shareMinor, wallet.available()) / 100 * 100;
        if (amount <= 0) {
            return;
        }
        wallet.hold(amount);
        payouts.save(PayoutRequest.auto(walletId, amount, wallet.getCurrency(), reference, Instant.now(clock)));
    }

    /** Отмена ещё не отправленных автовыплат по заказу (заказ отменён/возвращён до перевода). */
    @Transactional
    public void cancelQueuedAuto(String reference) {
        for (PayoutRequest payout : payouts.findByRequestedByAndStatus("auto:" + reference, PayoutRequest.Status.QUEUED)) {
            wallets.findWithLockById(payout.getWalletId()).orElseThrow().releaseHold(payout.getAmountMinor());
            payout.reject("system:order-cancelled", Instant.now(clock));
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> dueAutoPayouts(int limit) {
        return payouts.findDueAutoIds(PayoutRequest.Status.QUEUED, Instant.now(clock), PageRequest.of(0, limit));
    }

    /**
     * Шаг 1 отправки (своя транзакция, строка под блокировкой — два экземпляра сервиса не отправят
     * одно и то же): QUEUED → SENDING. Пусто — отправлять нечего (нет реквизитов, уже взята и т.п.).
     */
    @Transactional
    public Optional<AutoTransfer> startSending(UUID payoutId) {
        PayoutRequest payout = payouts.findWithLockById(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != PayoutRequest.Status.QUEUED) {
            return Optional.empty();
        }
        Wallet wallet = wallets.findById(payout.getWalletId()).orElseThrow();
        Optional<PayoutRequisite> requisite = requisites.approvedFor(wallet.getOwnerType(), wallet.getOwnerRef());
        if (requisite.isEmpty()) {
            payout.waitForRequisite(Instant.now(clock).plus(REQUISITE_WAIT));
            return Optional.empty();
        }
        payout.startSending(requisite.get().getId());
        return Optional.of(new AutoTransfer(payout.getId(), requisite.get(), payout.getAmountMinor()));
    }

    /** Шаг 2, успех: SENDING → PAID, hold → списание с кошелька. */
    @Transactional
    public void completeAuto(UUID payoutId, String providerPayoutId) {
        PayoutRequest payout = require(payoutId);
        Wallet wallet = wallets.findWithLockById(payout.getWalletId()).orElseThrow();
        wallet.releaseHold(payout.getAmountMinor());
        walletService.debit(payout.getWalletId(), payout.getCurrency(), payout.getAmountMinor(),
                WalletTransaction.Type.PAYOUT, "payout", payoutId.toString(), "payout:" + payoutId + ":DEBIT");
        payout.markSent(providerPayoutId, Instant.now(clock));
        publishStatus(payout, "PAID");
    }

    /** Шаг 2, временный сбой: обратно в очередь с паузой (1, 2, 4, 8… мин), после лимита — FAILED. */
    @Transactional
    public void retryAuto(UUID payoutId, String error) {
        PayoutRequest payout = require(payoutId);
        Instant now = Instant.now(clock);
        payout.retryLater(error, now.plus(Duration.ofMinutes(1L << Math.min(payout.getAttempts() - 1, 6))), now);
    }

    /** Шаг 2, отказ банка: FAILED, сумма остаётся в hold до решения админа. */
    @Transactional
    public void failAuto(UUID payoutId, String error) {
        require(payoutId).fail(error, Instant.now(clock));
    }

    /** Админ: повторить FAILED-автовыплату (например, после исправления реквизитов магазином). */
    @Transactional
    public void retry(UUID payoutId) {
        require(payoutId).requeue(Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public Page<PayoutRequest> byStatus(PayoutRequest.Status status, Pageable pageable) {
        return payouts.findByStatusOrderByCreatedAtAsc(status, pageable);
    }

    public record AutoTransfer(UUID payoutId, PayoutRequisite requisite, long amountMinor) {
    }

    // ─── ручные заявки ───────────────────────────────────────────────────────

    @Transactional
    public PayoutRequest request(UUID walletId, long amountMinor, String requestedBy, String bankDetailsJson) {
        Wallet wallet = wallets.findWithLockById(walletId)
                .orElseThrow(() -> new DomainException(FinanceErrors.WALLET_NOT_FOUND, "wallet not found: " + walletId));
        if (amountMinor <= 0) {
            throw new DomainException(FinanceErrors.PAYMENT_AMOUNT_INVALID, "amount must be positive");
        }
        wallet.hold(amountMinor);
        PayoutRequest payout = payouts.save(new PayoutRequest(
                walletId, amountMinor, wallet.getCurrency(), requestedBy, bankDetailsJson));
        events.publish(Topics.WALLET, walletId.toString(),
                EventEnvelope.of(EventTypes.PAYOUT_REQUESTED, PRODUCER, Tracing.currentTraceId(),
                        new WalletEvents.PayoutRequested(payout.getId(), walletId, amountMinor, wallet.getCurrency())));
        return payout;
    }

    @Transactional
    public void approve(UUID payoutId, String adminRef) {
        PayoutRequest payout = require(payoutId);
        Wallet wallet = wallets.findWithLockById(payout.getWalletId()).orElseThrow();
        wallet.releaseHold(payout.getAmountMinor());
        walletService.debit(payout.getWalletId(), payout.getCurrency(), payout.getAmountMinor(),
                WalletTransaction.Type.PAYOUT, "payout", payoutId.toString(), "payout:" + payoutId + ":DEBIT");
        payout.approve(adminRef, Instant.now(clock));
        publishStatus(payout, "APPROVED");
        // реальный перевод в MVP — вручную; сразу помечаем PAID
        payout.markPaid("manual", Instant.now(clock));
        publishStatus(payout, "PAID");
    }

    @Transactional
    public void reject(UUID payoutId, String adminRef) {
        PayoutRequest payout = require(payoutId);
        Wallet wallet = wallets.findWithLockById(payout.getWalletId()).orElseThrow();
        wallet.releaseHold(payout.getAmountMinor());
        payout.reject(adminRef, Instant.now(clock));
        // отдельного типа события для отказа нет — notification-service узнаёт по запросу статуса
    }

    @Transactional(readOnly = true)
    public Page<PayoutRequest> pending(Pageable pageable) {
        return payouts.findByStatusOrderByCreatedAtAsc(PayoutRequest.Status.REQUESTED, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PayoutRequest> forWallet(UUID walletId, Pageable pageable) {
        return payouts.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
    }

    private PayoutRequest require(UUID id) {
        return payouts.findById(id)
                .orElseThrow(() -> new DomainException(FinanceErrors.PAYOUT_NOT_FOUND, "payout not found: " + id));
    }

    private void publishStatus(PayoutRequest payout, String status) {
        String eventType = "PAID".equals(status) ? EventTypes.PAYOUT_PAID : EventTypes.PAYOUT_APPROVED;
        events.publish(Topics.WALLET, payout.getWalletId().toString(),
                EventEnvelope.of(eventType, PRODUCER, Tracing.currentTraceId(),
                        new WalletEvents.PayoutStatusChanged(payout.getId(), payout.getAmountMinor(),
                                payout.getCurrency(), status)));
    }
}
