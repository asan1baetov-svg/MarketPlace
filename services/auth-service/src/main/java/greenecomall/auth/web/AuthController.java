package greenecomall.auth.web;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.account.AuthenticationService;
import greenecomall.auth.account.RegistrationService;
import greenecomall.auth.domain.OtpPurpose;
import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.otp.OtpService;
import greenecomall.auth.repo.UserRepository;
import greenecomall.auth.support.Contacts;
import greenecomall.auth.token.IssuedTokens;
import greenecomall.auth.token.TokenService;
import greenecomall.auth.web.dto.LoginRequest;
import greenecomall.auth.web.dto.MeResponse;
import greenecomall.auth.web.dto.OtpRequest;
import greenecomall.auth.web.dto.OtpVerifyRequest;
import greenecomall.auth.web.dto.RefreshRequest;
import greenecomall.auth.web.dto.RegisterRequest;
import greenecomall.auth.web.dto.TokenResponse;
import greenecomall.common.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Регистрация, вход и выпуск токенов внешнего клиента. Контракт — docs/TASK-01-auth-service.md §3.1.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final UserRepository users;

    public AuthController(RegistrationService registrationService,
                          AuthenticationService authenticationService,
                          OtpService otpService,
                          TokenService tokenService,
                          UserRepository users) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request.email(), request.phone(), request.password(), request.locale());
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestOtp(@Valid @RequestBody OtpRequest request) {
        String target = Contacts.normalize(request.target());
        otpService.issue(target, Contacts.inferChannel(target), parsePurpose(request.purpose()));
    }

    @PostMapping("/otp/verify")
    public TokenResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        String target = Contacts.normalize(request.target());
        OtpPurpose purpose = parsePurpose(request.purpose());
        IssuedTokens tokens = switch (purpose) {
            case REGISTRATION -> registrationService.confirmRegistration(target, request.code());
            case LOGIN -> authenticationService.loginWithOtp(target, request.code());
            case RESET -> throw new DomainException(
                    AuthErrors.OTP_PURPOSE_UNSUPPORTED, "purpose 'reset' is not supported yet");
        };
        return TokenResponse.from(tokens);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authenticationService.loginWithPassword(request.login(), request.password()));
    }

    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(tokenService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revoke(request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        User user = users.findById(userId)
                .orElseThrow(() -> new DomainException(AuthErrors.CREDENTIALS_INVALID, "user not found"));
        Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
        return new MeResponse(
                user.getId(), user.getEmail(), user.getPhone(), roles,
                user.getClientType().name().toLowerCase(), jwt.getClaimAsString("mlm_user_id"));
    }

    private static OtpPurpose parsePurpose(String raw) {
        try {
            return OtpPurpose.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException(AuthErrors.OTP_PURPOSE_UNSUPPORTED, "unknown purpose: " + raw);
        }
    }
}
