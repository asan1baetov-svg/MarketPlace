package greenecomall.order.repo;

import greenecomall.order.domain.SuborderItem;
import greenecomall.order.domain.SuborderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SuborderItemRepository extends JpaRepository<SuborderItem, UUID> {

    List<SuborderItem> findBySuborderId(UUID suborderId);

    List<SuborderItem> findBySuborderIdIn(List<UUID> suborderIds);

    /** Сколько раз клиент получил этот товар (подзаказ в одном из {@code statuses}); &gt; 0 — покупал. */
    @Query("""
            select count(i) from SuborderItem i, Suborder s, Order o
            where i.suborderId = s.id and s.orderId = o.id
              and o.clientUserId = :clientUserId and i.productId = :productId and s.status in :statuses""")
    long countReceived(@Param("clientUserId") UUID clientUserId, @Param("productId") UUID productId,
                           @Param("statuses") Collection<SuborderStatus> statuses);
}
