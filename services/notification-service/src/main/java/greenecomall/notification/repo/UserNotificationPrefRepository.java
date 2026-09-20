package greenecomall.notification.repo;

import greenecomall.notification.domain.UserNotificationPref;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserNotificationPrefRepository extends JpaRepository<UserNotificationPref, UserNotificationPref.Key> {

    List<UserNotificationPref> findByUserId(UUID userId);
}
