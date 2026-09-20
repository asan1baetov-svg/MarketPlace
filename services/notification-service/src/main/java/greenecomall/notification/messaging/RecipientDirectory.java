package greenecomall.notification.messaging;

import greenecomall.notification.domain.RecipientLink;
import greenecomall.notification.repo.RecipientLinkRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Обёртка над read-model {@link RecipientLink}: запомнить/найти пользователя за id агрегата. */
@Component
public class RecipientDirectory {

    private final RecipientLinkRepository links;

    public RecipientDirectory(RecipientLinkRepository links) {
        this.links = links;
    }

    @Transactional
    public void remember(RecipientLink.RefType type, Object refId, UUID userId) {
        if (refId == null || userId == null) {
            return;
        }
        links.save(new RecipientLink(type, refId.toString(), userId));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> find(RecipientLink.RefType type, Object refId) {
        if (refId == null) {
            return Optional.empty();
        }
        return links.findById(new RecipientLink.Key(type, refId.toString())).map(RecipientLink::getUserId);
    }
}
