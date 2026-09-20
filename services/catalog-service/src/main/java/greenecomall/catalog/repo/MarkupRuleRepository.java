package greenecomall.catalog.repo;

import greenecomall.catalog.domain.MarkupRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarkupRuleRepository extends JpaRepository<MarkupRule, UUID> {

    List<MarkupRule> findByActiveTrue();

    Optional<MarkupRule> findFirstByScopeAndProductIdAndActiveTrue(
            greenecomall.catalog.domain.MarkupScope scope, UUID productId);

    Optional<MarkupRule> findFirstByScopeAndShopIdAndActiveTrueOrderByPriorityDesc(
            greenecomall.catalog.domain.MarkupScope scope, UUID shopId);

    Optional<MarkupRule> findFirstByScopeAndCategoryIdAndCityIdAndActiveTrueOrderByPriorityDesc(
            greenecomall.catalog.domain.MarkupScope scope, UUID categoryId, UUID cityId);

    Optional<MarkupRule> findFirstByScopeAndCategoryIdAndActiveTrueOrderByPriorityDesc(
            greenecomall.catalog.domain.MarkupScope scope, UUID categoryId);

    Optional<MarkupRule> findFirstByScopeAndCityIdAndActiveTrueOrderByPriorityDesc(
            greenecomall.catalog.domain.MarkupScope scope, UUID cityId);

    Optional<MarkupRule> findFirstByScopeAndActiveTrueOrderByPriorityDesc(
            greenecomall.catalog.domain.MarkupScope scope);
}
