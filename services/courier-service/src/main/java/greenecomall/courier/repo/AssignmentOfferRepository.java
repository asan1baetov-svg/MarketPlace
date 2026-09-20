package greenecomall.courier.repo;

import greenecomall.courier.domain.AssignmentOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentOfferRepository extends JpaRepository<AssignmentOffer, UUID> {

    List<AssignmentOffer> findBySuborderId(UUID suborderId);

    Optional<AssignmentOffer> findFirstBySuborderIdAndCourierIdAndStatus(
            UUID suborderId, UUID courierId, AssignmentOffer.Status status);
}
