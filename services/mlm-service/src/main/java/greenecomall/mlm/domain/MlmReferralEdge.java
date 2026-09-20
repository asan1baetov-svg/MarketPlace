package greenecomall.mlm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Closure table реферального дерева: пара (предок, потомок) на любой глубине
 * (docs/ARCHITECTURE.md §3.6). Узел ссылается сам на себя с {@code depth = 0}.
 */
@Entity
@Table(name = "mlm_referral_tree")
@IdClass(MlmReferralEdge.Key.class)
public class MlmReferralEdge {

    @Id
    @Column(name = "ancestor_id", nullable = false, updatable = false)
    private UUID ancestorId;

    @Id
    @Column(name = "descendant_id", nullable = false, updatable = false)
    private UUID descendantId;

    @Column(nullable = false, updatable = false)
    private int depth;

    protected MlmReferralEdge() {
    }

    public MlmReferralEdge(UUID ancestorId, UUID descendantId, int depth) {
        this.ancestorId = ancestorId;
        this.descendantId = descendantId;
        this.depth = depth;
    }

    public UUID getAncestorId() {
        return ancestorId;
    }

    public UUID getDescendantId() {
        return descendantId;
    }

    public int getDepth() {
        return depth;
    }

    public static class Key implements Serializable {
        private UUID ancestorId;
        private UUID descendantId;

        public Key() {
        }

        public Key(UUID ancestorId, UUID descendantId) {
            this.ancestorId = ancestorId;
            this.descendantId = descendantId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(ancestorId, k.ancestorId)
                    && Objects.equals(descendantId, k.descendantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ancestorId, descendantId);
        }
    }
}
