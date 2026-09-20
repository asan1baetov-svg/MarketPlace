package greenecomall.mlm.repo;

import greenecomall.mlm.domain.MlmReferralEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MlmReferralTreeRepository extends JpaRepository<MlmReferralEdge, MlmReferralEdge.Key> {

    /** Предки узла (включая сам узел с depth=0), ближайшие первыми. */
    List<MlmReferralEdge> findByDescendantIdOrderByDepthAsc(UUID descendantId);

    /** Потомки узла до заданной глубины (без самого узла). */
    List<MlmReferralEdge> findByAncestorIdAndDepthBetweenOrderByDepthAsc(UUID ancestorId, int minDepth, int maxDepth);

    boolean existsByAncestorIdAndDescendantId(UUID ancestorId, UUID descendantId);
}
