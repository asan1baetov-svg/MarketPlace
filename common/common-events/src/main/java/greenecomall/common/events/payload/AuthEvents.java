package greenecomall.common.events.payload;

import java.util.UUID;

/**
 * Payload-контракты топика {@code auth}.
 */
public final class AuthEvents {

    public record UserRegistered(UUID userId, String email, String phone, String locale) {
    }

    /**
     * @param accessStatus статус доступа на стороне внешней MLM-системы из SSO-токена
     *                     (например {@code PENDING}/{@code ACTIVE}/{@code BLOCKED}), может быть null
     */
    public record MlmUserLinked(UUID userId, String mlmUserId, String referralCode, String uplineMlmUserId,
                                String accessStatus) {
    }

    private AuthEvents() {
    }
}
