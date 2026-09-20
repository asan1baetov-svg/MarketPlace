package greenecomall.order.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Разобранный конверт входящего события. Тело ({@code payload}) остаётся деревом до момента,
 * когда листенер знает конкретный record-тип и вызывает {@link #payload(Class)}.
 */
public final class InboundEvent {

    /** Конверт с телом-деревом — ровно поля {@code EventEnvelope}, кроме payload как {@link JsonNode}. */
    record Raw(UUID eventId, String eventType, JsonNode payload) {
    }

    private final Raw raw;
    private final ObjectMapper mapper;

    private InboundEvent(Raw raw, ObjectMapper mapper) {
        this.raw = raw;
        this.mapper = mapper;
    }

    public static InboundEvent parse(String json, ObjectMapper mapper) {
        return new InboundEvent(mapper.readValue(json, Raw.class), mapper);
    }

    public UUID eventId() {
        return raw.eventId();
    }

    public String eventType() {
        return raw.eventType();
    }

    public <T> T payload(Class<T> type) {
        return mapper.treeToValue(raw.payload(), type);
    }
}
