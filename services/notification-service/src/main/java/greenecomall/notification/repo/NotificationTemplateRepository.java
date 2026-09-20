package greenecomall.notification.repo;

import greenecomall.notification.domain.Channel;
import greenecomall.notification.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByCodeAndChannelAndLocale(String code, Channel channel, String locale);
}
