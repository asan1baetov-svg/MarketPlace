package greenecomall.finance.wallet;

import greenecomall.finance.domain.PayoutRequisite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Переводы для dev/тестов: только пишет в лог и «успешно» отвечает. */
@Component
@ConditionalOnProperty(prefix = "finance.payout", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockPayoutGateway implements PayoutGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPayoutGateway.class);

    @Override
    public String transfer(PayoutRequisite requisite, long amountMinor, String transactionId, String comment) {
        log.info("[mock payout] {} minor -> {} {} ({})", amountMinor, requisite.getBank(), requisite.getPhone(), comment);
        return "mock_transfer_" + transactionId;
    }
}
