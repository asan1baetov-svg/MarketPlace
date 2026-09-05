package greenecomall.auth.otp;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.config.AuthProperties;
import greenecomall.auth.domain.OtpChannel;
import greenecomall.auth.domain.OtpCode;
import greenecomall.auth.domain.OtpPurpose;
import greenecomall.auth.repo.OtpCodeRepository;
import greenecomall.auth.support.Hashing;
import greenecomall.common.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

/**
 * Выпуск и проверка одноразовых кодов. Хранится только хэш кода (привязанный к target).
 */
@Service
public class OtpService {

    private final OtpCodeRepository codes;
    private final OtpSender sender;
    private final AuthProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpCodeRepository codes, OtpSender sender, AuthProperties props, Clock clock) {
        this.codes = codes;
        this.sender = sender;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void issue(String target, OtpChannel channel, OtpPurpose purpose) {
        Instant now = clock.instant();
        codes.findFirstByTargetAndPurposeOrderByCreatedAtDesc(target, purpose).ifPresent(last -> {
            if (last.getCreatedAt() != null
                    && last.getCreatedAt().plus(props.otp().resendInterval()).isAfter(now)) {
                throw new DomainException(AuthErrors.OTP_TOO_FREQUENT, "otp requested too frequently");
            }
        });

        String code = randomDigits(props.otp().length());
        codes.save(new OtpCode(
                target, channel, Hashing.sha256Hex(target + ':' + code), purpose,
                now.plus(props.otp().ttl())));
        sender.send(target, channel, purpose, code);
    }

    /**
     * noRollbackFor: неудачная попытка должна увеличить счётчик даже при откате «наружу».
     */
    @Transactional(noRollbackFor = DomainException.class)
    public void verify(String target, OtpPurpose purpose, String code) {
        Instant now = clock.instant();
        OtpCode otp = codes
                .findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(target, purpose)
                .orElseThrow(() -> new DomainException(AuthErrors.OTP_INVALID, "no active code"));

        if (otp.isExpired(now)) {
            throw new DomainException(AuthErrors.OTP_EXPIRED, "code expired");
        }
        if (otp.attemptsExhausted()) {
            throw new DomainException(AuthErrors.OTP_ATTEMPTS_EXCEEDED, "too many attempts");
        }
        if (!otp.getCodeHash().equals(Hashing.sha256Hex(target + ':' + code))) {
            otp.registerFailedAttempt();
            throw new DomainException(AuthErrors.OTP_INVALID, "code mismatch");
        }
        otp.markConsumed(now);
    }

    private String randomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
