package greenecomall.mlm.domain;

/**
 * Жизненный цикл активации внутреннего клиента (ТЗ §4.2):
 * {@code NONE → MUST_PURCHASE (оплатил доступ, идёт окно) → ACTIVE (купил на X в срок) | EXPIRED}.
 * Повторная оплата доступа из {@code EXPIRED} перезапускает окно (docs/ARCHITECTURE.md §8.2 вопрос 2).
 */
public enum AccessStatus {
    NONE,
    MUST_PURCHASE,
    ACTIVE,
    EXPIRED
}
