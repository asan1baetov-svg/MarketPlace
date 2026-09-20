package greenecomall.mlm.core;

import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.payload.AuthEvents;
import greenecomall.mlm.config.MlmProperties;
import greenecomall.mlm.domain.AccessStatus;
import greenecomall.mlm.domain.MlmAccount;
import greenecomall.mlm.domain.MlmOrder;
import greenecomall.mlm.domain.MlmTariff;
import greenecomall.mlm.integration.MlmSyncClient;
import greenecomall.mlm.integration.MlmSyncOutbox;
import greenecomall.mlm.repo.MlmAccountRepository;
import greenecomall.mlm.repo.MlmOrderRepository;
import greenecomall.mlm.repo.MlmTariffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private MlmAccountRepository accounts;
    @Mock private MlmOrderRepository orders;
    @Mock private MlmTariffRepository tariffs;
    @Mock private ReferralService referrals;
    @Mock private MlmSyncOutbox syncOutbox;
    @Mock private DomainEventPublisher events;

    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");
    private AccountService service;
    private MlmTariff tariff;

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts, orders, tariffs, referrals, syncOutbox, events,
                new MlmProperties(false, List.of(3, 1), null), Clock.fixed(now, ZoneOffset.UTC));
        tariff = new MlmTariff("Базовый", null, 1_000_000, "KGS", 500_000, 30, true);
        ReflectionTestUtils.setField(tariff, "id", UUID.randomUUID());
    }

    private MlmAccount persisted(MlmAccount a) {
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        return a;
    }

    private List<String> publishedTypes() {
        ArgumentCaptor<EventEnvelope<?>> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events, atLeastOnce()).publish(anyString(), anyString(), env.capture());
        return env.getAllValues().stream().map(EventEnvelope::eventType).toList();
    }

    @Test
    void ssoLinkWithActiveExternalStatus_createsAccountAndOpensWindow() {
        UUID userId = UUID.randomUUID();
        when(accounts.findByMlmUserId("U1")).thenReturn(Optional.empty());
        when(tariffs.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(tariff));
        when(accounts.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0)));

        MlmAccount account = service.link(new AuthEvents.MlmUserLinked(userId, "U1", "REF", "UPLINE", "ACTIVE"));

        assertThat(account.getAccessStatus()).isEqualTo(AccessStatus.MUST_PURCHASE);
        assertThat(account.getActivationDeadline()).isEqualTo(now.plus(Duration.ofDays(30)));
        verify(referrals).attach(account);
        assertThat(publishedTypes()).containsExactly(EventTypes.MLM_ACCESS_ACTIVATED);
    }

    @Test
    void ssoLinkWithPendingStatus_keepsAccountInactive() {
        when(accounts.findByMlmUserId("U2")).thenReturn(Optional.empty());
        when(tariffs.findFirstByIsDefaultTrueAndActiveTrue()).thenReturn(Optional.of(tariff));
        when(accounts.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0)));

        MlmAccount account = service.link(new AuthEvents.MlmUserLinked(UUID.randomUUID(), "U2", null, null, "PENDING"));

        assertThat(account.getAccessStatus()).isEqualTo(AccessStatus.NONE);
        verify(events, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void paidOrderReachingThreshold_activatesAndSyncsBothPurchaseAndActivation() {
        MlmAccount account = persisted(new MlmAccount(UUID.randomUUID(), "U1", null, null, "KGS"));
        account.startActivationWindow(tariff, now.minus(Duration.ofDays(2)));
        UUID orderId = UUID.randomUUID();
        MlmOrder order = new MlmOrder(orderId, account.getId(), 600_000, "KGS");
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(orders.findByMlmAccountIdOrderByCreatedAtDesc(account.getId())).thenReturn(List.of(order));

        service.onOrderPaid(orderId, now);

        assertThat(account.getAccessStatus()).isEqualTo(AccessStatus.ACTIVE);
        assertThat(order.getStatus()).isEqualTo(MlmOrder.Status.COUNTED);
        assertThat(publishedTypes()).containsExactly(
                EventTypes.MLM_PURCHASE_COUNTED, EventTypes.MLM_CONDITION_MET, EventTypes.MLM_ACTIVATED);
        verify(syncOutbox).enqueue(any(MlmSyncClient.Purchase.class));
        ArgumentCaptor<MlmSyncClient.ActivationStatus> activation = ArgumentCaptor.forClass(MlmSyncClient.ActivationStatus.class);
        verify(syncOutbox).enqueue(activation.capture());
        assertThat(activation.getValue().status()).isEqualTo("CONDITION_MET");
        assertThat(activation.getValue().marketplaceOrderIds()).containsExactly(orderId);
    }

    @Test
    void paidOrderOfAlreadyActiveClient_isReportedButNotCounted() {
        MlmAccount account = persisted(new MlmAccount(UUID.randomUUID(), "U1", null, null, "KGS"));
        UUID orderId = UUID.randomUUID();
        MlmOrder order = new MlmOrder(orderId, account.getId(), 100_000, "KGS");
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        service.onOrderPaid(orderId, now);

        assertThat(order.getStatus()).isEqualTo(MlmOrder.Status.PAID);
        verify(syncOutbox).enqueue(any(MlmSyncClient.Purchase.class));
        verify(events, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void overdueAccountsExpireAndAreSynced() {
        MlmAccount account = persisted(new MlmAccount(UUID.randomUUID(), "U1", null, null, "KGS"));
        account.startActivationWindow(tariff, now.minus(Duration.ofDays(31)));
        when(accounts.findByAccessStatus(AccessStatus.MUST_PURCHASE)).thenReturn(List.of(account));

        assertThat(service.expireOverdue()).isEqualTo(1);

        assertThat(account.getAccessStatus()).isEqualTo(AccessStatus.EXPIRED);
        assertThat(publishedTypes()).containsExactly(EventTypes.MLM_EXPIRED);
        ArgumentCaptor<MlmSyncClient.ActivationStatus> activation = ArgumentCaptor.forClass(MlmSyncClient.ActivationStatus.class);
        verify(syncOutbox).enqueue(activation.capture());
        assertThat(activation.getValue().status()).isEqualTo("EXPIRED");
    }

    @Test
    void reminderIsSentOncePerThreshold() {
        MlmAccount account = persisted(new MlmAccount(UUID.randomUUID(), "U1", null, null, "KGS"));
        // окно 30 дней, до дедлайна ~2.5 дня → порог «за 3 дня»
        account.startActivationWindow(tariff, now.minus(Duration.ofDays(27)).minus(Duration.ofHours(12)));
        when(accounts.findByAccessStatus(AccessStatus.MUST_PURCHASE)).thenReturn(List.of(account));

        assertThat(service.sendDeadlineReminders()).isEqualTo(1);
        assertThat(service.sendDeadlineReminders()).isZero();
        assertThat(account.getLastReminderDays()).isEqualTo(3);
        assertThat(publishedTypes()).containsExactly(EventTypes.SEND_NOTIFICATION);
    }

    @Test
    void accessPaidWithFullTariffPrice_opensWindow() {
        MlmAccount account = persisted(new MlmAccount(UUID.randomUUID(), "U1", null, null, "KGS"));
        when(tariffs.findById(tariff.getId())).thenReturn(Optional.of(tariff));
        when(accounts.findByMlmUserId("U1")).thenReturn(Optional.of(account));

        service.onAccessPaid("U1", tariff.getId(), 1_000_000, "KGS", now);

        assertThat(account.getAccessStatus()).isEqualTo(AccessStatus.MUST_PURCHASE);
        assertThat(publishedTypes()).containsExactly(EventTypes.MLM_ACCESS_ACTIVATED);
    }

    @Test
    void accessUnderpaidOrInWrongCurrency_doesNotOpenWindow() {
        when(tariffs.findById(tariff.getId())).thenReturn(Optional.of(tariff));

        service.onAccessPaid("U1", tariff.getId(), 999_999, "KGS", now);
        service.onAccessPaid("U1", tariff.getId(), 1_000_000, "USD", now);

        verify(accounts, never()).findByMlmUserId(anyString());
        verify(events, never()).publish(anyString(), anyString(), any());
    }
}
