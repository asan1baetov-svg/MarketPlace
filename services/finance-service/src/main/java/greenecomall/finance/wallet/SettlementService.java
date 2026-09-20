package greenecomall.finance.wallet;

import greenecomall.common.events.payload.OrderEvents;
import greenecomall.finance.config.FinanceProperties;
import greenecomall.finance.domain.OrderSettlementPlan;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.domain.WalletTransaction;
import greenecomall.finance.repo.OrderSettlementPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Распределение средств по {@code payments.OrderPaid} (docs/ARCHITECTURE.md §2.4):
 * магазину — {@code cost_amount}, платформе — {@code goods − cost} (комиссия). План снимается
 * заранее из {@code orders.OrderCreated}, чтобы не звать order-service синхронно.
 *
 * <p>Входящая нога (деньги эквайринга на счёт платформы) в MVP не проводится отдельной строкой —
 * баланс кошельков виртуальный; учёт эквайринга — в {@code payments}/{@code payment_events}.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final OrderSettlementPlanRepository plans;
    private final WalletService walletService;
    private final PayoutService payoutService;
    private final FinanceProperties properties;
    private final ObjectMapper objectMapper;

    public SettlementService(OrderSettlementPlanRepository plans, WalletService walletService,
                             PayoutService payoutService, FinanceProperties properties, ObjectMapper objectMapper) {
        this.plans = plans;
        this.walletService = walletService;
        this.payoutService = payoutService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void capturePlan(OrderEvents.OrderCreated event) {
        if (plans.existsById(event.orderId())) {
            return;
        }
        List<OrderSettlementPlan.Line> lines = event.suborders().stream()
                .map(s -> new OrderSettlementPlan.Line(s.suborderId(), s.shopId(),
                        s.goodsAmountMinor(), s.costAmountMinor()))
                .toList();
        String json = objectMapper.writeValueAsString(new OrderSettlementPlan.Lines(lines));
        plans.save(new OrderSettlementPlan(event.orderId(), event.currency(), json));
    }

    @Transactional
    public void settleOrderPaid(Payment payment, UUID eventId) {
        OrderSettlementPlan plan = plans.findById(payment.getOrderId()).orElse(null);
        if (plan == null) {
            log.warn("no settlement plan for order {} (OrderCreated not seen yet)", payment.getOrderId());
            return;
        }
        if (plan.getStatus() != OrderSettlementPlan.Status.PENDING) {
            return;
        }
        String currency = plan.getCurrency();
        for (OrderSettlementPlan.Line line : parse(plan).items()) {
            walletService.credit(WalletOwnerType.SHOP, line.shopId().toString(), currency,
                    line.costAmountMinor(), WalletTransaction.Type.ORDER_SETTLEMENT,
                    "suborder", line.suborderId().toString(),
                    eventId + ":" + line.shopId() + ":SETTLEMENT");
            walletService.credit(WalletOwnerType.PLATFORM, properties.commissionOwnerRef(), currency,
                    line.commissionMinor(), WalletTransaction.Type.COMMISSION,
                    "suborder", line.suborderId().toString(),
                    eventId + ":commission:" + line.suborderId());
            // Часть магазина сразу уходит ему переводом; комиссия платформы остаётся на счёте эквайринга
            Wallet shopWallet = walletService.require(WalletOwnerType.SHOP, line.shopId().toString(), currency);
            payoutService.queueAuto(shopWallet.getId(), line.costAmountMinor(), autoPayoutRef(payment.getOrderId()));
        }
        plan.markSettled();
    }

    @Transactional
    public void reverseOrder(UUID orderId, UUID eventId) {
        OrderSettlementPlan plan = plans.findById(orderId).orElse(null);
        if (plan == null || plan.getStatus() != OrderSettlementPlan.Status.SETTLED) {
            return;
        }
        // Ещё не отправленные переводы магазинам по этому заказу просто отменяем
        payoutService.cancelQueuedAuto(autoPayoutRef(orderId));
        String currency = plan.getCurrency();
        Wallet commissionWallet = walletService.require(
                WalletOwnerType.PLATFORM, properties.commissionOwnerRef(), currency);
        for (OrderSettlementPlan.Line line : parse(plan).items()) {
            Wallet shopWallet = walletService.require(WalletOwnerType.SHOP, line.shopId().toString(), currency);
            // Если магазину уже перевели — кошелёк уходит в минус, долг гасится из следующих продаж
            walletService.debitAllowingDebt(shopWallet.getId(), currency, line.costAmountMinor(),
                    WalletTransaction.Type.REFUND, "suborder", line.suborderId().toString(),
                    eventId + ":" + line.shopId() + ":REFUND");
            walletService.debit(commissionWallet.getId(), currency, line.commissionMinor(),
                    WalletTransaction.Type.REFUND, "suborder", line.suborderId().toString(),
                    eventId + ":commission-refund:" + line.suborderId());
        }
        plan.markReversed();
    }

    private static String autoPayoutRef(UUID orderId) {
        return "order:" + orderId;
    }

    private OrderSettlementPlan.Lines parse(OrderSettlementPlan plan) {
        return objectMapper.readValue(plan.getLinesJson(), OrderSettlementPlan.Lines.class);
    }
}
