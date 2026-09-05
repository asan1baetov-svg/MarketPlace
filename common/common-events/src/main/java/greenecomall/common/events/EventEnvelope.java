package greenecomall.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Единый конверт для всех доменных событий в Kafka.
 * Тело события ({@code payload}) — один из record-ов пакета {@code greenecomall.common.events.payload}.
 *
 * @param eventId    уникальный id события, используется консюмерами для идемпотентности
 * @param eventType  тип, см. {@link EventTypes} (например {@code orders.OrderCreated})
 * @param version    версия схемы payload, начинается с 1
 * @param occurredAt момент возникновения события в источнике
 * @param producer   имя сервиса-источника
 * @param traceId    сквозной идентификатор трассировки
 * @param payload    тело события
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        String traceId,
        T payload
) {
    public static <T> EventEnvelope<T> of(String eventType, String producer, String traceId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, Instant.now(), producer, traceId, payload);
    }
}
