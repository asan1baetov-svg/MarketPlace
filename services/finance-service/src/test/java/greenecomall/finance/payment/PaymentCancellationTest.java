package greenecomall.finance.payment;

import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.finance.config.FinanceProperties;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.domain.PaymentStatus;
import greenecomall.finance.repo.PaymentEventRepository;
import greenecomall.finance.repo.PaymentRepository;
import greenecomall.finance.repo.RefundRepository;
import greenecomall.finance.wallet.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Отмена заказа доходит до денег: неоплаченный платёж закрывается, оплаченный — возвращается клиенту. */
@ExtendWith(MockitoExtension.class)
class PaymentCancellationTest {

    private static final String SECRET = "s3cr3t";

    @Mock private PaymentRepository payments;
    @Mock private PaymentEventRepository paymentEvents;
    @Mock private RefundRepository refunds;
    @Mock private SettlementService settlementService;
    @Mock private DomainEventPublisher events;

    private PaymentService service;
    private final UUID orderId = UUID.randomUUID();
    private Payment payment;

    @BeforeEach
    void setUp() {
        MockAcquiringProvider provider = new MockAcquiringProvider(new FinanceProperties(null, null, SECRET, null, null));
        service = new PaymentService(payments, paymentEvents, refunds, provider, settlementService, events,
                JsonMapper.builder().build(), Clock.systemUTC());
        payment = Payment.forOrder(orderId, UUID.randomUUID(), 24_000, "KGS", "mock");
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.attachProviderPaymentId("mock_1");
        lenient().when(payments.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        lenient().when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        lenient().when(refunds.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cancelledBeforePayment_closesPayment() {
        service.onOrderCancelled(orderId, "client changed mind", UUID.randomUUID());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(events, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void cancelledAfterPayment_refundsClientAndReversesWallets() {
        payment.markSucceeded();

        service.onOrderCancelled(orderId, "refund: damaged", UUID.randomUUID());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<EventEnvelope<?>> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events).publish(anyString(), anyString(), env.capture());
        assertThat(env.getValue().eventType()).isEqualTo(EventTypes.PAYMENT_REFUNDED);
        verify(settlementService).reverseOrder(any(), any());
    }

    @Test
    void successArrivingAfterCancellation_isRefundedNotPaid() {
        service.onOrderCancelled(orderId, "timeout", UUID.randomUUID());
        when(payments.findByProviderPaymentId("mock_1")).thenReturn(Optional.of(payment));
        String body = "{\"providerEventId\":\"ev1\",\"providerPaymentId\":\"mock_1\",\"status\":\"succeeded\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.set(MockAcquiringProvider.SIGNATURE_HEADER, MockAcquiringProvider.hmacSha256Hex(SECRET, body));
        service.handleWebhook(headers, body);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(events, never()).publish(anyString(), anyString(), any());
        verify(settlementService, never()).settleOrderPaid(any(), any());
    }
}
