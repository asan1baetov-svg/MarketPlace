package greenecomall.finance.wallet;

import greenecomall.common.events.payload.OrderEvents;
import greenecomall.finance.config.FinanceProperties;
import greenecomall.finance.domain.OrderSettlementPlan;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.domain.WalletTransaction;
import greenecomall.finance.repo.OrderSettlementPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock private OrderSettlementPlanRepository plans;
    @Mock private WalletService walletService;
    @Mock private PayoutService payoutService;

    private SettlementService service;
    private final FinanceProperties props = new FinanceProperties(null, null, null, null, null);

    @BeforeEach
    void setUp() {
        service = new SettlementService(plans, walletService, payoutService, props, JsonMapper.builder().build());
    }

    @Test
    void orderPaid_creditsShopCostAndPlatformCommissionPerSuborder() {
        UUID orderId = UUID.randomUUID();
        UUID shopA = UUID.randomUUID();
        UUID shopB = UUID.randomUUID();
        OrderEvents.OrderCreated created = new OrderEvents.OrderCreated(orderId, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderEvents.SuborderLine(UUID.randomUUID(), shopA, 24_000, 20_000),
                        new OrderEvents.SuborderLine(UUID.randomUUID(), shopB, 5_000, 4_000)),
                29_000, "KGS");

        ArgumentCaptor<OrderSettlementPlan> saved = ArgumentCaptor.forClass(OrderSettlementPlan.class);
        service.capturePlan(created);
        verify(plans).save(saved.capture());
        OrderSettlementPlan plan = saved.getValue();
        when(plans.findById(orderId)).thenReturn(Optional.of(plan));

        Payment payment = Payment.forOrder(orderId, UUID.randomUUID(), 29_000, "KGS", "mock");
        Wallet walletA = wallet(shopA);
        Wallet walletB = wallet(shopB);
        when(walletService.require(WalletOwnerType.SHOP, shopA.toString(), "KGS")).thenReturn(walletA);
        when(walletService.require(WalletOwnerType.SHOP, shopB.toString(), "KGS")).thenReturn(walletB);
        UUID eventId = UUID.randomUUID();
        service.settleOrderPaid(payment, eventId);

        // магазинам сразу уходит ровно их часть (себестоимость), комиссия остаётся у платформы
        verify(payoutService).queueAuto(walletA.getId(), 20_000L, "order:" + orderId);
        verify(payoutService).queueAuto(walletB.getId(), 4_000L, "order:" + orderId);

        verify(walletService).credit(eq(WalletOwnerType.SHOP), eq(shopA.toString()), eq("KGS"), eq(20_000L),
                eq(WalletTransaction.Type.ORDER_SETTLEMENT), anyString(), anyString(), anyString());
        verify(walletService).credit(eq(WalletOwnerType.SHOP), eq(shopB.toString()), eq("KGS"), eq(4_000L),
                eq(WalletTransaction.Type.ORDER_SETTLEMENT), anyString(), anyString(), anyString());
        verify(walletService).credit(eq(WalletOwnerType.PLATFORM), eq("platform.commission"), eq("KGS"), eq(4_000L),
                eq(WalletTransaction.Type.COMMISSION), anyString(), anyString(), anyString());
        verify(walletService).credit(eq(WalletOwnerType.PLATFORM), eq("platform.commission"), eq("KGS"), eq(1_000L),
                eq(WalletTransaction.Type.COMMISSION), anyString(), anyString(), anyString());
        assertThat(plan.getStatus()).isEqualTo(OrderSettlementPlan.Status.SETTLED);
    }

    @Test
    void orderPaid_withoutPlan_doesNothing() {
        UUID orderId = UUID.randomUUID();
        when(plans.findById(orderId)).thenReturn(Optional.empty());
        service.settleOrderPaid(Payment.forOrder(orderId, UUID.randomUUID(), 1, "KGS", "mock"), UUID.randomUUID());
        verify(walletService, never()).credit(any(), any(), any(), any(Long.class), any(), any(), any(), any());
    }

    private static Wallet wallet(UUID shopId) {
        Wallet wallet = new Wallet(WalletOwnerType.SHOP, shopId.toString(), "KGS");
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        return wallet;
    }
}
