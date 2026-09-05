package greenecomall.common.events;

/**
 * Порт публикации доменных событий. Реализация в каждом сервисе — через transactional outbox:
 * бизнес-данные и запись в таблицу {@code outbox} коммитятся одной транзакцией,
 * отдельный публикатор досылает событие в Kafka.
 */
public interface DomainEventPublisher {

    /**
     * @param topic имя топика из {@link Topics}
     * @param key   ключ партиции — id корневого агрегата
     * @param event конверт события
     */
    void publish(String topic, String key, EventEnvelope<?> event);
}
