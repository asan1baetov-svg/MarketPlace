package greenecomall.order.order;

import greenecomall.order.config.OrderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Раз в минуту отменяет заказы, не оплаченные в течение {@code order.reservation-timeout},
 * и снимает их резервы остатков (docs/ARCHITECTURE.md §2.3).
 */
@Component
public class ReservationTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationTimeoutJob.class);

    private final OrderService orderService;
    private final OrderProperties properties;
    private final Clock clock;

    public ReservationTimeoutJob(OrderService orderService, OrderProperties properties, Clock clock) {
        this.orderService = orderService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${order.reservation-sweep-interval:60s}")
    public void sweep() {
        Instant cutoff = Instant.now(clock).minus(properties.reservationTimeout());
        int cancelled = orderService.cancelExpiredUnpaid(cutoff);
        if (cancelled > 0) {
            log.info("reservation timeout: cancelled {} unpaid order(s) older than {}", cancelled, cutoff);
        }
    }
}
