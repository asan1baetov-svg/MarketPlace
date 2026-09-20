package greenecomall.finance.wallet;

import greenecomall.common.events.DomainEventPublisher;
import greenecomall.finance.domain.PayoutRequest;
import greenecomall.finance.domain.PayoutRequisite;
import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.finik.FinikBank;
import greenecomall.finance.repo.PayoutRequestRepository;
import greenecomall.finance.repo.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Очередь автовыплат магазинам: сколько ставится, куда и что происходит при ответах банка. */
@ExtendWith(MockitoExtension.class)
class AutoPayoutTest {

    @Mock private PayoutRequestRepository payouts;
    @Mock private WalletRepository wallets;
    @Mock private WalletService walletService;
    @Mock private PayoutRequisiteService requisites;
    @Mock private DomainEventPublisher events;
    @Mock private PayoutGateway gateway;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
    private PayoutService service;
    private AutoPayoutJob job;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        service = new PayoutService(payouts, wallets, walletService, requisites, events, clock);
        job = new AutoPayoutJob(service, gateway);
        wallet = new Wallet(WalletOwnerType.SHOP, "shop-1", "KGS");
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        lenient().when(wallets.findWithLockById(wallet.getId())).thenReturn(Optional.of(wallet));
        lenient().when(wallets.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        lenient().when(payouts.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    }

    @Test
    void queueAuto_holdsShopShareRoundedDownToWholeSom() {
        wallet.applyCredit(20_050);

        service.queueAuto(wallet.getId(), 20_050, "order:1");

        ArgumentCaptor<PayoutRequest> saved = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(payouts).save(saved.capture());
        assertThat(saved.getValue().getAmountMinor()).isEqualTo(20_000);
        assertThat(saved.getValue().getStatus()).isEqualTo(PayoutRequest.Status.QUEUED);
        assertThat(wallet.available()).isEqualTo(50);
    }

    @Test
    void queueAuto_shopInDebt_paysOnlyWhatIsAvailable() {
        wallet.applyDebitAllowingDebt(15_000);   // долг после возврата уже выплаченного заказа
        wallet.applyCredit(20_000);              // новая продажа

        service.queueAuto(wallet.getId(), 20_000, "order:2");

        ArgumentCaptor<PayoutRequest> saved = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(payouts).save(saved.capture());
        assertThat(saved.getValue().getAmountMinor()).isEqualTo(5_000);
    }

    @Test
    void queueAuto_nothingAvailable_queuesNothing() {
        wallet.applyDebitAllowingDebt(10_000);
        wallet.applyCredit(5_000);

        service.queueAuto(wallet.getId(), 5_000, "order:3");

        verify(payouts, never()).save(any());
    }

    @Test
    void withoutApprovedRequisite_payoutWaitsAndNothingIsSent() {
        PayoutRequest payout = queued(20_000);
        when(requisites.approvedFor(WalletOwnerType.SHOP, "shop-1")).thenReturn(Optional.empty());

        job.process(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutRequest.Status.QUEUED);
        assertThat(payout.getAttempts()).isZero();
        verify(gateway, never()).transfer(any(), anyLong(), anyString(), anyString());
    }

    @Test
    void successfulTransfer_marksPaidAndDebitsWallet() {
        PayoutRequest payout = queued(20_000);
        PayoutRequisite requisite = approvedRequisite();
        when(gateway.transfer(eq(requisite), eq(20_000L), eq(payout.getId().toString()), anyString())).thenReturn("fin-1");
        doAnswer(inv -> wallet.applyDebit(inv.getArgument(2))).when(walletService)
                .debit(any(), anyString(), anyLong(), any(), anyString(), anyString(), anyString());

        job.process(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutRequest.Status.PAID);
        assertThat(payout.getProviderPayoutId()).isEqualTo("fin-1");
        assertThat(wallet.available()).isZero();
        assertThat(wallet.getBalanceMinor()).isZero();
        verify(walletService).debit(eq(wallet.getId()), eq("KGS"), eq(20_000L), any(), anyString(), anyString(), anyString());
    }

    @Test
    void bankRejection_failsPayoutAndKeepsMoneyOnHold() {
        PayoutRequest payout = queued(20_000);
        approvedRequisite();
        when(gateway.transfer(any(), anyLong(), anyString(), anyString()))
                .thenThrow(new PayoutGateway.PayoutRejectedException("account not found"));

        job.process(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutRequest.Status.FAILED);
        assertThat(payout.getLastError()).contains("account not found");
        assertThat(wallet.available()).isZero();
        verify(walletService, never()).debit(any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void temporaryFailure_isRetriedThenFailsAfterLimit() {
        PayoutRequest payout = queued(20_000);
        approvedRequisite();
        when(gateway.transfer(any(), anyLong(), anyString(), anyString())).thenThrow(new RuntimeException("timeout"));

        for (int i = 1; i < PayoutRequest.MAX_ATTEMPTS; i++) {
            job.process(payout.getId());
            assertThat(payout.getStatus()).isEqualTo(PayoutRequest.Status.QUEUED);
        }
        job.process(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutRequest.Status.FAILED);
        assertThat(payout.getAttempts()).isEqualTo(PayoutRequest.MAX_ATTEMPTS);
    }

    @Test
    void cancelledOrderBeforeSending_releasesHold() {
        wallet.applyCredit(20_000);
        PayoutRequest payout = queued(20_000);
        wallet.hold(20_000);
        when(payouts.findByRequestedByAndStatus("auto:order:9", PayoutRequest.Status.QUEUED))
                .thenReturn(java.util.List.of(payout));

        service.cancelQueuedAuto("order:9");

        assertThat(payout.getStatus()).isEqualTo(PayoutRequest.Status.REJECTED);
        assertThat(wallet.available()).isEqualTo(20_000);
    }

    private PayoutRequest queued(long amount) {
        PayoutRequest payout = withId(PayoutRequest.auto(wallet.getId(), amount, "KGS", "order:x", clock.instant()));
        lenient().when(payouts.findWithLockById(payout.getId())).thenReturn(Optional.of(payout));
        lenient().when(payouts.findById(payout.getId())).thenReturn(Optional.of(payout));
        if (wallet.available() < amount && wallet.getBalanceMinor() == 0) {
            wallet.applyCredit(amount);
            wallet.hold(amount);
        }
        return payout;
    }

    private PayoutRequisite approvedRequisite() {
        PayoutRequisite requisite = new PayoutRequisite(WalletOwnerType.SHOP, "shop-1", FinikBank.OPTIMA_BANK,
                "996555123456", clock.instant());
        ReflectionTestUtils.setField(requisite, "id", UUID.randomUUID());
        requisite.approve(clock.instant());
        when(requisites.approvedFor(WalletOwnerType.SHOP, "shop-1")).thenReturn(Optional.of(requisite));
        return requisite;
    }

    private static PayoutRequest withId(PayoutRequest payout) {
        if (payout.getId() == null) {
            ReflectionTestUtils.setField(payout, "id", UUID.randomUUID());
        }
        return payout;
    }
}
