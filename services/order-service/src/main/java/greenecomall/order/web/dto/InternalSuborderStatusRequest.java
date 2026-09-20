package greenecomall.order.web.dto;

import greenecomall.order.domain.SuborderStatus;
import jakarta.validation.constraints.NotNull;

public record InternalSuborderStatusRequest(
        @NotNull SuborderStatus status,
        String actor,
        String reason) {

    public String actorOrDefault() {
        return actor == null || actor.isBlank() ? "internal" : actor;
    }
}
