package greenecomall.auth.mlm;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.domain.MlmSsoIdentity;
import greenecomall.auth.domain.MlmSsoTokenLog;
import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.repo.MlmSsoIdentityRepository;
import greenecomall.auth.repo.MlmSsoTokenLogRepository;
import greenecomall.auth.repo.RoleRepository;
import greenecomall.auth.repo.UserRepository;
import greenecomall.auth.support.Contacts;
import greenecomall.auth.support.Tracing;
import greenecomall.auth.token.IssuedTokens;
import greenecomall.auth.token.TokenService;
import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.AuthEvents;
import greenecomall.common.security.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Вход клиента из внешней MLM-системы по SSO-токену:
 * проверка токена → защита от повтора по jti → поиск/создание аккаунта → событие → выдача токенов.
 */
@Service
public class MlmSsoService {

    private static final String PRODUCER = "auth-service";

    public record SsoLogin(IssuedTokens tokens, boolean created) {
    }

    private final MlmIdentityProvider identityProvider;
    private final MlmSsoTokenLogRepository tokenLog;
    private final MlmSsoIdentityRepository identities;
    private final UserRepository users;
    private final RoleRepository roles;
    private final TokenService tokenService;
    private final DomainEventPublisher events;
    private final Clock clock;

    public MlmSsoService(MlmIdentityProvider identityProvider,
                         MlmSsoTokenLogRepository tokenLog,
                         MlmSsoIdentityRepository identities,
                         UserRepository users,
                         RoleRepository roles,
                         TokenService tokenService,
                         DomainEventPublisher events,
                         Clock clock) {
        this.identityProvider = identityProvider;
        this.tokenLog = tokenLog;
        this.identities = identities;
        this.users = users;
        this.roles = roles;
        this.tokenService = tokenService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public SsoLogin login(String rawToken) {
        MlmIdentity identity = identityProvider.verifyAndExtract(rawToken);

        if (tokenLog.existsById(identity.jti())) {
            throw new DomainException(AuthErrors.SSO_TOKEN_REPLAYED, "SSO token already used");
        }
        tokenLog.save(new MlmSsoTokenLog(
                identity.jti(), identity.mlmUserId(),
                identity.issuedAt() != null ? identity.issuedAt() : clock.instant()));

        Optional<MlmSsoIdentity> existing = identities.findByMlmUserId(identity.mlmUserId());
        if (existing.isPresent()) {
            MlmSsoIdentity link = existing.get();
            link.touchLogin(identity.accessStatus(), clock.instant());
            User user = link.getUser();
            if (user.getStatus() == UserStatus.BLOCKED) {
                throw new DomainException(AuthErrors.USER_BLOCKED, "user is blocked");
            }
            return new SsoLogin(tokenService.issueFor(user), false);
        }

        User user = createMlmUser(identity);
        MlmSsoIdentity link = new MlmSsoIdentity(
                user, identity.mlmUserId(), identity.referralCode(),
                identity.uplineMlmUserId(), identity.accessStatus());
        link.touchLogin(identity.accessStatus(), clock.instant());
        identities.save(link);

        events.publish(Topics.AUTH, user.getId().toString(),
                EventEnvelope.of(EventTypes.MLM_USER_LINKED, PRODUCER, Tracing.currentTraceId(),
                        new AuthEvents.MlmUserLinked(
                                user.getId(), identity.mlmUserId(),
                                identity.referralCode(), identity.uplineMlmUserId(), identity.accessStatus())));

        return new SsoLogin(tokenService.issueFor(user), true);
    }

    private User createMlmUser(MlmIdentity identity) {
        String email = Contacts.normalize(identity.email());
        String phone = Contacts.normalize(identity.phone());
        // Контакт уже занят локальным аккаунтом — не связываем автоматически (нужно решение по account linking).
        if (email != null && users.existsByEmail(email)) {
            email = null;
        }
        if (phone != null && users.existsByPhone(phone)) {
            phone = null;
        }
        User user = User.forMlmSso(email, phone, identity.locale());
        user.addRole(requireRole(Roles.CLIENT_MLM));
        return users.save(user);
    }

    private Role requireRole(String code) {
        return roles.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("role not seeded: " + code));
    }
}
