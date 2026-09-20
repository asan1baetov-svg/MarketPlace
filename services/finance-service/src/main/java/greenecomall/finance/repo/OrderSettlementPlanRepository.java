package greenecomall.finance.repo;

import greenecomall.finance.domain.OrderSettlementPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderSettlementPlanRepository extends JpaRepository<OrderSettlementPlan, UUID> {
}
