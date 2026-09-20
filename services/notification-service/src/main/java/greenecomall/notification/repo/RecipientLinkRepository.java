package greenecomall.notification.repo;

import greenecomall.notification.domain.RecipientLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientLinkRepository extends JpaRepository<RecipientLink, RecipientLink.Key> {
}
