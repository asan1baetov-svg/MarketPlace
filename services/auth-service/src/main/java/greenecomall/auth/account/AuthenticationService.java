package greenecomall.auth.account;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.domain.OtpPurpose;
import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.otp.OtpService;
import greenecomall.auth.repo.UserRepository;
import greenecomall.auth.support.Contacts;
import greenecomall.auth.token.IssuedTokens;
import greenecomall.auth.token.TokenService;
import greenecomall.common.domain.DomainException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход по паролю и вход по OTP. Сообщения об ошибках намеренно неинформативны
 * (не раскрываем, существует ли аккаунт).
 */
@Service
public class AuthenticationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final TokenService tokenService;

    public AuthenticationService(UserRepository users,
                                 PasswordEncoder passwordEncoder,
                                 OtpService otpService,
                                 TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.tokenService = tokenService;
    }

    @Transactional
    public IssuedTokens loginWithPassword(String login, String rawPassword) {
        User user = users.findByLogin(Contacts.normalize(login))
                .orElseThrow(() -> new DomainException(AuthErrors.CREDENTIALS_INVALID, "bad credentials"));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new DomainException(AuthErrors.USER_BLOCKED, "user is blocked");
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException(AuthErrors.CREDENTIALS_INVALID, "bad credentials");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DomainException(AuthErrors.USER_NOT_ACTIVE, "confirm registration first");
        }
        return tokenService.issueFor(user);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public IssuedTokens loginWithOtp(String target, String code) {
        String normalized = Contacts.normalize(target);
        otpService.verify(normalized, OtpPurpose.LOGIN, code);

        User user = users.findByLogin(normalized)
                .orElseThrow(() -> new DomainException(AuthErrors.CREDENTIALS_INVALID, "user not found"));
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new DomainException(AuthErrors.USER_BLOCKED, "user is blocked");
        }
        return tokenService.issueFor(user);
    }
}
