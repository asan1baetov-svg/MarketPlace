package greenecomall.order.order;

import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.OrderEvents;
import greenecomall.order.OrderErrors;
import greenecomall.order.cart.CartService;
import greenecomall.order.catalog.CatalogClient;
import greenecomall.order.domain.CartItem;
import greenecomall.order.domain.Order;
import greenecomall.order.domain.OrderStatus;
import greenecomall.order.domain.OrderStatusHistory;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderItem;
import greenecomall.order.domain.SuborderStatus;
import greenecomall.order.promo.PromocodeService;
import greenecomall.order.repo.OrderRepository;
import greenecomall.order.repo.OrderStatusHistoryRepository;
import greenecomall.order.repo.SuborderItemRepository;
import greenecomall.order.repo.SuborderRepository;
import greenecomall.order.support.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Оформление заказа и его жизненный цикл: мультивендорный checkout (корзина одного города →
 * {@code order} + {@code suborders} по магазинам), снимки цен, синхронный резерв остатков в
 * catalog-service, отмена по таймауту неоплаты, применение платёжных/курьерских событий.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String PRODUCER = "order-service";

    private final OrderRepository orders;
    private final SuborderRepository suborders;
    private final SuborderItemRepository suborderItems;
    private final OrderStatusHistoryRepository history;
    private final CartService cartService;
    private final CatalogClient catalog;
    private final PromocodeService promocodes;
    private final SuborderWorkflow workflow;
    private final DomainEventPublisher events;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orders, SuborderRepository suborders, SuborderItemRepository suborderItems,
                        OrderStatusHistoryRepository history, CartService cartService, CatalogClient catalog,
                        PromocodeService promocodes, SuborderWorkflow workflow, DomainEventPublisher events,
                        ObjectMapper objectMapper) {
        this.orders = orders;
        this.suborders = suborders;
        this.suborderItems = suborderItems;
        this.history = history;
        this.cartService = cartService;
        this.catalog = catalog;
        this.promocodes = promocodes;
        this.workflow = workflow;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CheckoutResult checkout(UUID clientUserId, Object deliveryAddress, String promoCode) {
        CartService.CartView cart = cartService.view(clientUserId);
        if (cart.items().isEmpty()) {
            throw new DomainException(OrderErrors.CART_EMPTY, "cart is empty for client " + clientUserId);
        }
        UUID cityId = cart.cityId();

        List<PricedLine> lines = new ArrayList<>();
        String currency = null;
        for (CartItem item : cart.items()) {
            CatalogClient.PriceView price = catalog.price(item.getProductId(), cityId);
            if (currency == null) {
                currency = price.currency();
            } else if (!currency.equals(price.currency())) {
                throw new DomainException(OrderErrors.CURRENCY_MISMATCH,
                        "mixed currencies in cart: " + currency + " vs " + price.currency());
            }
            lines.add(new PricedLine(item, price));
        }

        long itemsAmount = lines.stream().mapToLong(PricedLine::goodsMinor).sum();

        long discount = 0L;
        UUID promocodeId = null;
        if (promoCode != null && !promoCode.isBlank()) {
            PromocodeService.Applied applied = promocodes.apply(promoCode, itemsAmount);
            discount = applied.discountMinor();
            promocodeId = applied.promocodeId();
        }

        List<CartItem> reserved = new ArrayList<>();
        try {
            for (PricedLine line : lines) {
                catalog.reserve(line.item().getProductId(), line.item().getQty());
                reserved.add(line.item());
            }
        } catch (DomainException e) {
            reserved.forEach(i -> safeRelease(i.getProductId(), i.getQty()));
            throw e;
        }

        Order order = orders.save(new Order(clientUserId, cityId, toJson(deliveryAddress), currency,
                itemsAmount, discount, promocodeId));
        history.save(OrderStatusHistory.forOrder(order.getId(), null, OrderStatus.CREATED, "client:" + clientUserId, null));

        Map<UUID, List<PricedLine>> byShop = new LinkedHashMap<>();
        for (PricedLine line : lines) {
            byShop.computeIfAbsent(line.item().getShopId(), k -> new ArrayList<>()).add(line);
        }

        List<OrderEvents.SuborderLine> eventLines = new ArrayList<>();
        for (Map.Entry<UUID, List<PricedLine>> entry : byShop.entrySet()) {
            long goods = entry.getValue().stream().mapToLong(PricedLine::goodsMinor).sum();
            long cost = entry.getValue().stream().mapToLong(PricedLine::costMinor).sum();
            Suborder suborder = suborders.save(new Suborder(order.getId(), entry.getKey(), goods, cost));
            for (PricedLine line : entry.getValue()) {
                suborderItems.save(new SuborderItem(
                        suborder.getId(), line.item().getProductId(), line.item().getProductName(),
                        line.item().getQty(), line.price().costPriceMinor(), line.price().salePriceMinor(),
                        line.price().markupPercent()));
            }
            history.save(OrderStatusHistory.forSuborder(suborder.getId(), null, SuborderStatus.CREATED, "system", null));
            eventLines.add(new OrderEvents.SuborderLine(suborder.getId(), entry.getKey(), goods, cost));
        }

        if (promocodeId != null) {
            promocodes.recordUse(promocodeId);
        }
        cartService.clear(cart.cartId());

        events.publish(Topics.ORDERS, order.getId().toString(),
                EventEnvelope.of(EventTypes.ORDER_CREATED, PRODUCER, Tracing.currentTraceId(),
                        new OrderEvents.OrderCreated(order.getId(), clientUserId, cityId, eventLines,
                                order.getTotalAmountMinor(), currency)));

        return new CheckoutResult(order.getId(), order.getTotalAmountMinor(), currency);
    }

    @Transactional(readOnly = true)
    public boolean hasReceived(UUID clientUserId, UUID productId) {
        return suborderItems.countReceived(clientUserId, productId,
                EnumSet.of(SuborderStatus.DELIVERED, SuborderStatus.COMPLETED)) > 0;
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return orders.findById(id)
                .orElseThrow(() -> new DomainException(OrderErrors.ORDER_NOT_FOUND, "order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Suborder> suborders(UUID orderId) {
        return suborders.findByOrderId(orderId);
    }

    /** Данные для доставки посылки: адрес клиента и состав — их показывает приложение курьера. */
    @Transactional(readOnly = true)
    public Suborder requireSuborder(UUID suborderId) {
        return suborders.findById(suborderId)
                .orElseThrow(() -> new DomainException(OrderErrors.SUBORDER_NOT_FOUND, "suborder not found: " + suborderId));
    }

    @Transactional(readOnly = true)
    public List<SuborderItem> suborderItems(UUID suborderId) {
        return suborderItems.findBySuborderId(suborderId);
    }

    @Transactional(readOnly = true)
    public java.util.Map<UUID, List<SuborderItem>> itemsBySuborder(List<UUID> suborderIds) {
        if (suborderIds.isEmpty()) {
            return java.util.Map.of();
        }
        return suborderItems.findBySuborderIdIn(suborderIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(SuborderItem::getSuborderId));
    }

    @Transactional(readOnly = true)
    public Page<Order> listForClient(UUID clientUserId, Pageable pageable) {
        return orders.findByClientUserIdOrderByCreatedAtDesc(clientUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> adminSearch(OrderStatus status, UUID cityId, UUID clientId, Pageable pageable) {
        return orders.search(status, cityId, clientId, pageable);
    }

    /** Применяет {@code payments.OrderPaid}: заказ и все suborders CREATED → PAID. */
    @Transactional
    public void markPaid(UUID orderId, UUID paymentId) {
        Order order = get(orderId);
        if (order.getStatus() != OrderStatus.CREATED) {
            log.info("markPaid ignored: order {} is {}", orderId, order.getStatus());
            return;
        }
        OrderStatus before = order.getStatus();
        order.markPaid(paymentId);
        history.save(OrderStatusHistory.forOrder(orderId, before, OrderStatus.PAID, "payments", null));
        workflow.transitionAllOfOrder(orderId, SuborderStatus.PAID, "payments", null);
        commitReservations(orderId);
    }

    /** Применяет {@code payments.PaymentFailed}: заказ → PAYMENT_FAILED, снятие резервов. */
    @Transactional
    public void markPaymentFailed(UUID orderId, String reason) {
        Order order = get(orderId);
        if (order.getStatus() != OrderStatus.CREATED) {
            log.info("markPaymentFailed ignored: order {} is {}", orderId, order.getStatus());
            return;
        }
        OrderStatus before = order.getStatus();
        order.markPaymentFailed();
        history.save(OrderStatusHistory.forOrder(orderId, before, OrderStatus.PAYMENT_FAILED, "payments", reason));
        releaseReservations(orderId);
        workflow.transitionAllOfOrder(orderId, SuborderStatus.CANCELLED, "payments", reason);
        publishCancelled(order, reason);
    }

    @Transactional
    public void assignCourier(UUID suborderId, UUID courierId) {
        suborders.findById(suborderId).ifPresent(s -> s.assignCourier(courierId));
    }

    @Transactional
    public void cancel(UUID orderId, String reason, String actor) {
        Order order = get(orderId);
        boolean wasUnpaid = order.getStatus() == OrderStatus.CREATED;
        OrderStatus before = order.getStatus();
        order.cancel();
        history.save(OrderStatusHistory.forOrder(orderId, before, OrderStatus.CANCELLED, actor, reason));
        if (wasUnpaid) {
            releaseReservations(orderId);
        }
        workflow.transitionAllOfOrder(orderId, SuborderStatus.CANCELLED, actor, reason);
        publishCancelled(order, reason);
    }

    @Transactional
    public void refund(UUID orderId, String reason, String actor) {
        Order order = get(orderId);
        history.save(OrderStatusHistory.forOrder(orderId, order.getStatus(), order.getStatus(), actor, "refund: " + reason));
        workflow.transitionAllOfOrder(orderId, SuborderStatus.REFUNDED, actor, reason);
        // Компенсацию по кошелькам/эквайрингу делает finance-service по этому событию.
        publishCancelled(order, "refund: " + reason);
    }

    /** Отменяет заказы, не оплаченные в срок (docs/ARCHITECTURE.md §2.3 «таймаут неоплаченного резерва»). */
    @Transactional
    public int cancelExpiredUnpaid(java.time.Instant cutoff) {
        List<Order> expired = orders.findByStatusAndCreatedAtBefore(OrderStatus.CREATED, cutoff);
        for (Order order : expired) {
            cancel(order.getId(), "unpaid within reservation window", "system:timeout");
        }
        return expired.size();
    }

    private void releaseReservations(UUID orderId) {
        List<UUID> subIds = suborders.findByOrderId(orderId).stream().map(Suborder::getId).toList();
        for (SuborderItem item : suborderItems.findBySuborderIdIn(subIds)) {
            safeRelease(item.getProductId(), item.getQty());
        }
    }

    /**
     * Оплата превращает резерв в списание. Ошибка catalog не валит обработку события: повтор
     * {@code OrderPaid} списал бы уже списанное (commit не идемпотентен), а несписанный резерв
     * лишь занижает доступный остаток — это видно в логе и правится вручную.
     */
    private void commitReservations(UUID orderId) {
        List<UUID> subIds = suborders.findByOrderId(orderId).stream().map(Suborder::getId).toList();
        for (SuborderItem item : suborderItems.findBySuborderIdIn(subIds)) {
            try {
                catalog.commit(item.getProductId(), item.getQty());
            } catch (RuntimeException e) {
                log.error("stock commit failed for product {} qty {} of paid order {}",
                        item.getProductId(), item.getQty(), orderId, e);
            }
        }
    }

    private void safeRelease(UUID productId, int qty) {
        try {
            catalog.release(productId, qty);
        } catch (RuntimeException e) {
            log.warn("stock release failed for product {} qty {} (will not block)", productId, qty, e);
        }
    }

    private void publishCancelled(Order order, String reason) {
        List<UUID> subIds = suborders.findByOrderId(order.getId()).stream().map(Suborder::getId).toList();
        events.publish(Topics.ORDERS, order.getId().toString(),
                EventEnvelope.of(EventTypes.ORDER_CANCELLED, PRODUCER, Tracing.currentTraceId(),
                        new OrderEvents.OrderCancelled(order.getId(), reason, subIds)));
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        if (value instanceof String s) {
            return s.isBlank() ? "{}" : s;
        }
        return objectMapper.writeValueAsString(value);
    }

    private record PricedLine(CartItem item, CatalogClient.PriceView price) {

        long goodsMinor() {
            return price.salePriceMinor() * item.getQty();
        }

        long costMinor() {
            return price.costPriceMinor() * item.getQty();
        }
    }

    public record CheckoutResult(UUID orderId, long totalAmountMinor, String currency) {
    }
}
