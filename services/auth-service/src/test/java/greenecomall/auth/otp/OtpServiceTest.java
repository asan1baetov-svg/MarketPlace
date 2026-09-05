package greenecomall.auth.otp;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.config.AuthProperties;
import greenecomall.auth.domain.OtpChannel;
import greenecomall.auth.domain.OtpCode;
import greenecomall.auth.domain.OtpPurpose;
import greenecomall.auth.repo.OtpCodeRepository;
import greenecomall.auth.support.Hashing;
import greenecomall.common.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String TARGET = "client@example.com";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private OtpCodeRepository codes;
    @Mock
    private OtpSender sender;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AuthProperties props = new AuthProperties(
                null, null,
                new AuthProperties.Otp(Duration.ofMinutes(5), Duration.ofSeconds(60), 6),
                null, null, null);
        otpService = new OtpService(codes, sender, props, clock);
    }

    @Test
    void issue_sendsCodeAndPersistsItsHash() {
        when(codes.findFirstByTargetAndPurposeOrderByCreatedAtDesc(TARGET, OtpPurpose.REGISTRATION))
                .thenReturn(Optional.empty());

        otpService.issue(TARGET, OtpChannel.EMAIL, OtpPurpose.REGISTRATION);

        verify(sender).send(eq(TARGET), eq(OtpChannel.EMAIL), eq(OtpPurpose.REGISTRATION), any());
        verify(codes, times(1)).save(any(OtpCode.class));
    }

    @Test
    void issue_tooSoonAfterPrevious_isRejected() {
        OtpCode recent = new OtpCode(TARGET, OtpChannel.EMAIL, "hash", OtpPurpose.REGISTRATION, NOW.plusSeconds(300));
        ReflectionTestUtils.setField(recent, "createdAt", NOW.minusSeconds(30));
        when(codes.findFirstByTargetAndPurposeOrderByCreatedAtDesc(TARGET, OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> otpService.issue(TARGET, OtpChannel.EMAIL, OtpPurpose.REGISTRATION))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.OTP_TOO_FREQUENT);
    }

    @Test
    void verify_withExpiredCode_throws() {
        OtpCode code = codeWithValidHash(NOW.minusSeconds(1));
        when(codes.findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TARGET, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> otpService.verify(TARGET, OtpPurpose.LOGIN, "123456"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.OTP_EXPIRED);
    }

    @Test
    void verify_withExhaustedAttempts_throws() {
        OtpCode code = codeWithValidHash(NOW.plusSeconds(300));
        for (int i = 0; i < OtpCode.MAX_ATTEMPTS; i++) {
            code.registerFailedAttempt();
        }
        when(codes.findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TARGET, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> otpService.verify(TARGET, OtpPurpose.LOGIN, "123456"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.OTP_ATTEMPTS_EXCEEDED);
    }

    @Test
    void verify_withWrongCode_registersFailedAttemptAndThrows() {
        OtpCode code = codeWithValidHash(NOW.plusSeconds(300));
        when(codes.findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TARGET, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> otpService.verify(TARGET, OtpPurpose.LOGIN, "000000"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(AuthErrors.OTP_INVALID);
        assertThat(code.getAttempts()).isEqualTo(1);
        assertThat(code.isConsumed()).isFalse();
    }

    @Test
    void verify_withCorrectCode_marksConsumed() {
        OtpCode code = codeWithValidHash(NOW.plusSeconds(300));
        when(codes.findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(TARGET, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(code));

        otpService.verify(TARGET, OtpPurpose.LOGIN, "123456");

        assertThat(code.isConsumed()).isTrue();
    }

    private OtpCode codeWithValidHash(Instant expiresAt) {
        return new OtpCode(TARGET, OtpChannel.EMAIL, Hashing.sha256Hex(TARGET + ":123456"), OtpPurpose.LOGIN, expiresAt);
    }
}
