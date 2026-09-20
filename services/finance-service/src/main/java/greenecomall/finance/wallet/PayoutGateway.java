package greenecomall.finance.wallet;

import greenecomall.finance.domain.PayoutRequisite;

/**
 * Реальный перевод денег получателю со счёта платформы. Выбор реализации — {@code finance.payout.provider}.
 * Временный сбой (сеть, 5xx) — любое {@link RuntimeException}, перевод повторится;
 * окончательный отказ (неверные реквизиты, лимит банка) — {@link PayoutRejectedException}.
 */
public interface PayoutGateway {

    /**
     * @param transactionId постоянный id перевода (= id выплаты): повтор после таймаута не должен
     *                      провести перевод дважды
     * @return id перевода у провайдера
     */
    String transfer(PayoutRequisite requisite, long amountMinor, String transactionId, String comment);

    class PayoutRejectedException extends RuntimeException {
        public PayoutRejectedException(String message) {
            super(message);
        }
    }
}
