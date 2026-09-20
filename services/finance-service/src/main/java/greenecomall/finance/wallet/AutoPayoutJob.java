package greenecomall.finance.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Отправляет автовыплаты магазинам из очереди. Каждая выплата: SENDING (коммит) → вызов провайдера
 * вне транзакции → результат (коммит). Если сервис упадёт между вызовом и записью результата,
 * выплата останется в SENDING — её не повторяем автоматически (перевод мог пройти), это видно админу.
 */
@Component
@ConditionalOnProperty(prefix = "finance.payout", name = "auto-enabled", havingValue = "true", matchIfMissing = true)
public class AutoPayoutJob {

    private static final Logger log = LoggerFactory.getLogger(AutoPayoutJob.class);
    private static final int BATCH = 20;

    private final PayoutService payoutService;
    private final PayoutGateway gateway;

    public AutoPayoutJob(PayoutService payoutService, PayoutGateway gateway) {
        this.payoutService = payoutService;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${finance.payout.poll-interval:30s}")
    public void run() {
        for (UUID payoutId : payoutService.dueAutoPayouts(BATCH)) {
            process(payoutId);
        }
    }

    void process(UUID payoutId) {
        PayoutService.AutoTransfer transfer = payoutService.startSending(payoutId).orElse(null);
        if (transfer == null) {
            return;
        }
        String comment = "GreenEcoMall " + payoutId.toString().substring(0, 8).toUpperCase();
        try {
            String providerId = gateway.transfer(transfer.requisite(), transfer.amountMinor(), payoutId.toString(), comment);
            payoutService.completeAuto(payoutId, providerId);
            log.info("auto payout {} sent: {} minor, provider id {}", payoutId, transfer.amountMinor(), providerId);
        } catch (PayoutGateway.PayoutRejectedException e) {
            log.warn("auto payout {} rejected: {}", payoutId, e.getMessage());
            payoutService.failAuto(payoutId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("auto payout {} failed, will retry: {}", payoutId, e.toString());
            payoutService.retryAuto(payoutId, e.toString());
        }
    }
}
