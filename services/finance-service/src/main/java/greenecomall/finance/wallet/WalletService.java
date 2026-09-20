package greenecomall.finance.wallet;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.WalletEvents;
import greenecomall.finance.FinanceErrors;
import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletOwner;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.domain.WalletTransaction;
import greenecomall.finance.repo.WalletOwnerRepository;
import greenecomall.finance.repo.WalletRepository;
import greenecomall.finance.repo.WalletTransactionRepository;
import greenecomall.finance.support.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Кошельки + ledger двойной записи (append-only). Каждая проводка идемпотентна по
 * {@code idempotencyKey}; денормализованный {@code balance} двигается в той же транзакции.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private static final String PRODUCER = "finance-service";

    private final WalletRepository wallets;
    private final WalletOwnerRepository owners;
    private final WalletTransactionRepository transactions;
    private final DomainEventPublisher events;

    public WalletService(WalletRepository wallets, WalletOwnerRepository owners,
                         WalletTransactionRepository transactions, DomainEventPublisher events) {
        this.wallets = wallets;
        this.owners = owners;
        this.transactions = transactions;
        this.events = events;
    }

    @Transactional
    public Wallet getOrCreate(WalletOwnerType ownerType, String ownerRef, String currency) {
        return wallets.findByOwnerTypeAndOwnerRefAndCurrency(ownerType, ownerRef, currency)
                .orElseGet(() -> wallets.save(new Wallet(ownerType, ownerRef, currency)));
    }

    /** Привязка магазина/курьера к пользователю. Первый зарегистрированный владелец не перезаписывается. */
    @Transactional
    public void registerOwner(WalletOwnerType ownerType, String ownerRef, UUID userId) {
        if (!owners.existsById(new WalletOwner.Key(ownerType, ownerRef))) {
            owners.save(new WalletOwner(ownerType, ownerRef, userId));
        }
    }

    /** Кошелёк принадлежит пользователю (через владельца магазина/курьера). Кошельки платформы — никому. */
    @Transactional(readOnly = true)
    public boolean isOwnedBy(Wallet wallet, UUID userId) {
        return userId != null && owners.findById(new WalletOwner.Key(wallet.getOwnerType(), wallet.getOwnerRef()))
                .map(o -> o.getUserId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Wallet> ownedBy(UUID userId) {
        return wallets.findOwnedByUser(userId);
    }

    @Transactional(readOnly = true)
    public Wallet require(WalletOwnerType ownerType, String ownerRef, String currency) {
        return wallets.findByOwnerTypeAndOwnerRefAndCurrency(ownerType, ownerRef, currency)
                .orElseThrow(() -> new DomainException(FinanceErrors.WALLET_NOT_FOUND,
                        "no wallet for " + ownerType + ":" + ownerRef + " " + currency));
    }

    @Transactional(readOnly = true)
    public Wallet require(UUID walletId) {
        return wallets.findById(walletId)
                .orElseThrow(() -> new DomainException(FinanceErrors.WALLET_NOT_FOUND, "wallet not found: " + walletId));
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> history(UUID walletId, Pageable pageable) {
        return transactions.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
    }

    /** Зачисление на кошелёк. Повторный вызов с тем же {@code idempotencyKey} — no-op. */
    @Transactional
    public void credit(WalletOwnerType ownerType, String ownerRef, String currency, long amountMinor,
                       WalletTransaction.Type type, String referenceType, String referenceId, String idempotencyKey) {
        if (transactions.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("credit skipped, duplicate idempotencyKey {}", idempotencyKey);
            return;
        }
        Wallet wallet = getOrCreate(ownerType, ownerRef, currency);
        assertCurrency(wallet, currency);
        Wallet locked = wallets.findWithLockById(wallet.getId()).orElseThrow();
        long balanceAfter = locked.applyCredit(amountMinor);
        transactions.save(new WalletTransaction(locked.getId(), WalletTransaction.Direction.CREDIT, amountMinor,
                currency, type, referenceType, referenceId, balanceAfter, idempotencyKey));
        events.publish(Topics.WALLET, locked.getId().toString(),
                EventEnvelope.of(EventTypes.WALLET_CREDITED, PRODUCER, Tracing.currentTraceId(),
                        new WalletEvents.WalletCredited(locked.getId(), ownerType.name(), ownerRef,
                                amountMinor, currency, type.name(), referenceId)));
    }

    /** Списание с кошелька (например, обратная проводка при отмене/возврате). Идемпотентно. */
    @Transactional
    public void debit(UUID walletId, String currency, long amountMinor, WalletTransaction.Type type,
                      String referenceType, String referenceId, String idempotencyKey) {
        debit(walletId, currency, amountMinor, type, referenceType, referenceId, idempotencyKey, false);
    }

    /**
     * Списание, которое может увести баланс в минус: обратная проводка по возврату, когда деньги
     * магазину уже переведены. Долг гасится следующими зачислениями (автовыплата берёт только доступное).
     */
    @Transactional
    public void debitAllowingDebt(UUID walletId, String currency, long amountMinor, WalletTransaction.Type type,
                                  String referenceType, String referenceId, String idempotencyKey) {
        debit(walletId, currency, amountMinor, type, referenceType, referenceId, idempotencyKey, true);
    }

    private void debit(UUID walletId, String currency, long amountMinor, WalletTransaction.Type type,
                       String referenceType, String referenceId, String idempotencyKey, boolean allowDebt) {
        if (transactions.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("debit skipped, duplicate idempotencyKey {}", idempotencyKey);
            return;
        }
        Wallet locked = wallets.findWithLockById(walletId)
                .orElseThrow(() -> new DomainException(FinanceErrors.WALLET_NOT_FOUND, "wallet not found: " + walletId));
        assertCurrency(locked, currency);
        long balanceAfter = allowDebt ? locked.applyDebitAllowingDebt(amountMinor) : locked.applyDebit(amountMinor);
        transactions.save(new WalletTransaction(walletId, WalletTransaction.Direction.DEBIT, amountMinor,
                currency, type, referenceType, referenceId, balanceAfter, idempotencyKey));
        events.publish(Topics.WALLET, walletId.toString(),
                EventEnvelope.of(EventTypes.WALLET_DEBITED, PRODUCER, Tracing.currentTraceId(),
                        new WalletEvents.WalletDebited(walletId, amountMinor, currency, type.name(), referenceId)));
    }

    private void assertCurrency(Wallet wallet, String currency) {
        if (!wallet.getCurrency().equals(currency)) {
            throw new DomainException(FinanceErrors.CURRENCY_MISMATCH,
                    "wallet " + wallet.getId() + " is " + wallet.getCurrency() + ", got " + currency);
        }
    }
}
