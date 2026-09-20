package greenecomall.order.domain;

import greenecomall.common.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static Order newOrder() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), "{}", "KGS", 10_000, 0, null);
    }

    private static Suborder suborderWith(SuborderStatus status) {
        Suborder s = new Suborder(UUID.randomUUID(), UUID.randomUUID(), 5_000, 4_000);
        ReflectionTestUtils.setField(s, "status", status);
        return s;
    }

    @Test
    void totalIsItemsMinusDiscountFlooredAtZero() {
        Order o = new Order(UUID.randomUUID(), UUID.randomUUID(), "{}", "KGS", 10_000, 3_000, UUID.randomUUID());
        assertThat(o.getTotalAmountMinor()).isEqualTo(7_000);
    }

    @Test
    void markPaidOnlyFromCreated() {
        Order o = newOrder();
        o.markPaid(UUID.randomUUID());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThatThrownBy(() -> o.markPaid(UUID.randomUUID())).isInstanceOf(DomainException.class);
    }

    @Test
    void recomputeStatus_allCompleted_completesOrder() {
        Order o = newOrder();
        o.recomputeStatus(List.of(suborderWith(SuborderStatus.COMPLETED), suborderWith(SuborderStatus.COMPLETED)));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void recomputeStatus_someDeliveredSomeOpen_isPartiallyDelivered() {
        Order o = newOrder();
        o.recomputeStatus(List.of(suborderWith(SuborderStatus.DELIVERED), suborderWith(SuborderStatus.ACCEPTED)));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PARTIALLY_DELIVERED);
    }

    @Test
    void recomputeStatus_allCancelled_cancelsOrder() {
        Order o = newOrder();
        o.recomputeStatus(List.of(suborderWith(SuborderStatus.CANCELLED), suborderWith(SuborderStatus.CANCELLED)));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
