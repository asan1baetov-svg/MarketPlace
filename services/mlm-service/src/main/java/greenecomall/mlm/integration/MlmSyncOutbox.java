package greenecomall.mlm.integration;

import greenecomall.mlm.domain.MlmSyncMessage;
import greenecomall.mlm.repo.MlmSyncMessageRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** Постановка сообщений обратной синхронизации в outbox (в транзакции вызывающего). */
@Component
public class MlmSyncOutbox {

    private final MlmSyncMessageRepository messages;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MlmSyncOutbox(MlmSyncMessageRepository messages, ObjectMapper objectMapper, Clock clock) {
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueue(MlmSyncClient.ActivationStatus status) {
        messages.save(new MlmSyncMessage(MlmSyncMessage.Type.ACTIVATION_STATUS,
                objectMapper.writeValueAsString(status), clock.instant()));
    }

    public void enqueue(MlmSyncClient.Purchase purchase) {
        messages.save(new MlmSyncMessage(MlmSyncMessage.Type.PURCHASE_REPORTED,
                objectMapper.writeValueAsString(purchase), clock.instant()));
    }
}
