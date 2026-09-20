package greenecomall.mlm.repo;

import greenecomall.mlm.domain.MlmOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MlmOrderRepository extends JpaRepository<MlmOrder, UUID> {

    List<MlmOrder> findByMlmAccountIdOrderByCreatedAtDesc(UUID mlmAccountId);

    /** Выручка от MLM-клиентов (оплаченные, не отменённые заказы) — для отчёта админа. */
    @Query("""
            select coalesce(sum(o.amountMinor), 0) from MlmOrder o
            where o.status in (greenecomall.mlm.domain.MlmOrder.Status.COUNTED,
                               greenecomall.mlm.domain.MlmOrder.Status.PAID)
            """)
    long sumPaidRevenue();
}
