package greenecomall.finance.payment;

import greenecomall.finance.domain.Payment;
import org.springframework.http.HttpHeaders;

import java.util.Optional;

/**
 * Абстракция эквайринг-провайдера (docs/ARCHITECTURE.md §8.2 вопрос 6 — один провайдер в MVP,
 * подключаемые по стране позже). Реальные данные карт не проходят через сервис: только
 * hosted payment page / QR провайдера (R5). Выбор реализации — {@code finance.acquiring.provider}.
 */
public interface PaymentProvider {

    String name();

    /** Инициирует платёж на стороне провайдера, возвращает id платежа у провайдера и ссылку на оплату. */
    HostedPage initiate(Payment payment);

    /** Проверяет подпись и разбирает входящий webhook в общий формат. Не бросает на плохой подписи. */
    WebhookEvent parseWebhook(HttpHeaders headers, String rawBody);

    /**
     * Возврат клиенту. Пусто — у провайдера нет API возврата, возврат делается вручную
     * в кабинете провайдера и подтверждается админом.
     */
    Optional<String> refund(Payment payment, long amountMinor);

    record HostedPage(String providerPaymentId, String redirectUrl) {
    }

    /**
     * @param status      {@code succeeded} / {@code failed} / иное (игнорируется)
     * @param amountMinor сумма, которую провайдер реально списал; null — провайдер её не сообщил
     */
    record WebhookEvent(boolean signatureValid, String providerEventId, String providerPaymentId,
                        String status, String reason, Long amountMinor) {
    }
}
