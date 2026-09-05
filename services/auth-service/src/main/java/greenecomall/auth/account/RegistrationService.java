package greenecomall.auth.account;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.domain.OtpChannel;
import greenecomall.auth.domain.OtpPurpose;
import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.otp.OtpService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Регистрация внешнего клиента: создание PENDING-аккаунта + отправка OTP,
 * затем подтверждение кода → активация → выдача токенов.
 */
@Service
public class RegistrationService {

    private static final String PRODUCER = "auth-service";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final DomainEventPublisher events;

    public RegistrationService(UserRepository users,
                               RoleRepository roles,
                               PasswordEncoder passwordEncoder,
                               OtpService otpService,
                               TokenService tokenService,
                               DomainEventPublisher events) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.events = events;
    }

    @Transactional
    public UUID register(String email, String phone, String rawPassword, String locale) {
        String normalizedEmail = Contacts.normalize(email);
        String normalizedPhone = Contacts.normalize(phone);
        if (normalizedEmail == null && normalizedPhone == null) {
            throw new DomainException(AuthErrors.CREDENTIALS_INVALID, "email or phone is required");
        }
        if (normalizedEmail != null && users.existsByEmail(normalizedEmail)) {
            throw new DomainException(AuthErrors.CONTACT_TAKEN, "email already registered");
        }
        if (normalizedPhone != null && users.existsByPhone(normalizedPhone)) {
            throw new DomainException(AuthErrors.CONTACT_TAKEN, "phone already registered");
        }

        User user = User.forLocalRegistration(
                normalizedEmail, normalizedPhone, passwordEncoder.encode(rawPassword), locale);
        user.addRole(requireRole(Roles.CLIENT_EXTERNAL));
        users.save(user);

        String target = normalizedEmail != null ? normalizedEmail : normalizedPhone;
        OtpChannel channel = normalizedEmail != null ? OtpChannel.EMAIL : OtpChannel.SMS;
        otpService.issue(target, channel, OtpPurpose.REGISTRATION);

        events.publish(Topics.AUTH, user.getId().toString(),
                EventEnvelope.of(EventTypes.USER_REGISTERED, PRODUCER, Tracing.currentTraceId(),
                        new AuthEvents.UserRegistered(
                                user.getId(), normalizedEmail, normalizedPhone, user.getLocale())));
        return user.getId();
    }

    /**
     * noRollbackFor: см. {@code OtpService.verify} — счётчик неудачных попыток должен пережить ошибку.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public IssuedTokens confirmRegistration(String target, String code) {
        String normalized = Contacts.normalize(target);
        otpService.verify(normalized, OtpPurpose.REGISTRATION, code);

        User user = users.findByLogin(normalized)
                .orElseThrow(() -> new DomainException(AuthErrors.CREDENTIALS_INVALID, "user not found"));
        user.activate();
        return tokenService.issueFor(user);
    }

    private Role requireRole(String code) {
        return roles.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("role not seeded: " + code));
    }
}
