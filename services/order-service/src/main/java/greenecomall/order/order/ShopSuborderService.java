package greenecomall.order.order;

import greenecomall.common.domain.DomainException;
import greenecomall.order.OrderErrors;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderStatus;
import greenecomall.order.repo.SuborderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Действия магазина над своими suborders. Магазин видит {@code cost_amount}, но не наценку/цену
 * продажи (см. docs/ARCHITECTURE.md §2.3) — за это отвечает DTO слоя web.
 */
@Service
public class ShopSuborderService {

    private final SuborderRepository suborders;
    private final SuborderWorkflow workflow;

    public ShopSuborderService(SuborderRepository suborders, SuborderWorkflow workflow) {
        this.suborders = suborders;
        this.workflow = workflow;
    }

    @Transactional(readOnly = true)
    public Page<Suborder> list(UUID shopId, SuborderStatus status, Pageable pageable) {
        return status == null
                ? suborders.findByShopIdOrderByCreatedAtDesc(shopId, pageable)
                : suborders.findByShopIdAndStatusOrderByCreatedAtDesc(shopId, status, pageable);
    }

    @Transactional
    public void accept(UUID suborderId, UUID shopId) {
        requireOwned(suborderId, shopId);
        workflow.transition(suborderId, SuborderStatus.ACCEPTED, "shop:" + shopId, null);
    }

    @Transactional
    public void assemble(UUID suborderId, UUID shopId) {
        requireOwned(suborderId, shopId);
        workflow.transition(suborderId, SuborderStatus.ASSEMBLED, "shop:" + shopId, null);
    }

    private Suborder requireOwned(UUID suborderId, UUID shopId) {
        Suborder suborder = suborders.findById(suborderId)
                .orElseThrow(() -> new DomainException(OrderErrors.SUBORDER_NOT_FOUND, "suborder not found: " + suborderId));
        if (!suborder.getShopId().equals(shopId)) {
            throw new DomainException(OrderErrors.SUBORDER_FORBIDDEN, "suborder " + suborderId + " belongs to another shop");
        }
        return suborder;
    }
}
