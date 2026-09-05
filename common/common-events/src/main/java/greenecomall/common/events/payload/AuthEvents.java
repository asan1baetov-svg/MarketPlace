package greenecomall.common.events.payload;

import java.util.UUID;

/**
 * Payload-контракты топика {@code auth}.
 */
public final class AuthEvents {

    public record UserRegistered(UUID userId, String email, String phone, String locale) {
    }

    public record MlmUserLinked(UUID userId, String mlmUserId, String referralCode, String uplineMlmUserId) {
    }

    private AuthEvents() {
    }
}
