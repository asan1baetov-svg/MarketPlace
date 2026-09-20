package greenecomall.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Конечный автомат suborder (см. docs/ARCHITECTURE.md §2.3):
 * {@code CREATED → PAID → ACCEPTED → ASSEMBLED → HANDED_TO_COURIER → IN_TRANSIT → DELIVERED → COMPLETED}
 * с ветками {@code CANCELLED} / {@code REFUNDED}.
 */
public enum SuborderStatus {
    CREATED,
    PAID,
    ACCEPTED,
    ASSEMBLED,
    HANDED_TO_COURIER,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDED;

    private static final Set<SuborderStatus> TERMINAL = EnumSet.of(COMPLETED, CANCELLED, REFUNDED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** Разрешён ли прямой переход {@code this → target}. */
    public boolean canTransitionTo(SuborderStatus target) {
        return switch (this) {
            case CREATED -> target == PAID || target == CANCELLED;
            case PAID -> target == ACCEPTED || target == CANCELLED || target == REFUNDED;
            case ACCEPTED -> target == ASSEMBLED || target == CANCELLED || target == REFUNDED;
            case ASSEMBLED -> target == HANDED_TO_COURIER || target == CANCELLED || target == REFUNDED;
            case HANDED_TO_COURIER -> target == IN_TRANSIT || target == CANCELLED || target == REFUNDED;
            case IN_TRANSIT -> target == DELIVERED || target == CANCELLED || target == REFUNDED;
            case DELIVERED -> target == COMPLETED || target == REFUNDED;
            case COMPLETED, CANCELLED, REFUNDED -> false;
        };
    }
}
