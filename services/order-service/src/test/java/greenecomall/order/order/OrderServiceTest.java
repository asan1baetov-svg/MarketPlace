package greenecomall.order.order;

import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;
import greenecomall.order.domain.SuborderItem;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.OrderStatus;
import greenecomall.order.domain.Order;
import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.order.OrderErrors;
import greenecomall.order.cart.CartService;
import greenecomall.order.catalog.CatalogClient;
import greenecomall.order.domain.CartItem;
import greenecomall.order.promo.PromocodeService;
import greenecomall.order.repo.OrderRepository;
import greenecomall.order.repo.OrderStatusHistoryRepository;
import greenecomall.order.repo.SuborderItemRepository;
import greenecomall.order.repo.SuborderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orders;
    @Mock private SuborderRepository suborders;
    @Mock private SuborderItemRepository suborderItems;
    @Mock private OrderStatusHistoryRepository history;
    @Mock private CartService cartService;
    @Mock private CatalogClient catalog;
    @Mock private PromocodeService promocodes;
    @Mock private SuborderWorkflow workflow;
    @Mock private DomainEventPublisher events;

    private OrderService service;

    private final UUID client = UUID.randomUUID();
    private final UUID cartId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, suborders, suborderItems, history, cartService, catalog,
                promocodes, workflow, events, null);
    }

    @Test
    void checkout_emptyCart_isRejected() {
        when(cartService.view(client)).thenReturn(new CartService.CartView(null, null, List.of()));
        assertThatThrownBy(() -> service.checkout(client, "{}", null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(OrderErrors.CART_EMPTY);
    }

    @Test
    void checkout_mixedCurrencies_isRejected() {
        UUID cityId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        CartItem i1 = new CartItem(cartId, p1, UUID.randomUUID(), "A", 1, 1000, "KGS");
        CartItem i2 = new CartItem(cartId, p2, UUID.randomUUID(), "B", 1, 1000, "USD");
        when(cartService.view(client)).thenReturn(new CartService.CartView(cartId, cityId, List.of(i1, i2)));
        when(catalog.price(eq(p1), any())).thenReturn(new CatalogClient.PriceView(p1, 800, 1000, BigDecimal.TEN, "KGS"));
        when(catalog.price(eq(p2), any())).thenReturn(new CatalogClient.PriceView(p2, 800, 1000, BigDecimal.TEN, "USD"));

        assertThatThrownBy(() -> service.checkout(client, "{}", null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(OrderErrors.CURRENCY_MISMATCH);
    }

    @Test
    void markPaid_commitsReservedStock_andToleratesCatalogFailure() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(client, UUID.randomUUID(), "{}", "KGS", 3_000, 0, null);
        Suborder sub = new Suborder(orderId, UUID.randomUUID(), 3_000, 2_400);
        ReflectionTestUtils.setField(sub, "id", UUID.randomUUID());
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(suborders.findByOrderId(orderId)).thenReturn(List.of(sub));
        when(suborderItems.findBySuborderIdIn(List.of(sub.getId()))).thenReturn(List.of(
                new SuborderItem(sub.getId(), p1, "A", 2, 800, 1000, BigDecimal.TEN),
                new SuborderItem(sub.getId(), p2, "B", 1, 800, 1000, BigDecimal.TEN)));
        doThrow(new RuntimeException("catalog down")).when(catalog).commit(p1, 2);

        service.markPaid(orderId, UUID.randomUUID());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(catalog).commit(p1, 2);
        verify(catalog).commit(p2, 1);
    }
}
