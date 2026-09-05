package greenecomall.auth.otp;

import greenecomall.auth.domain.OtpChannel;
import greenecomall.auth.domain.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Заглушка доставки на время разработки — код пишется в лог.
 * TODO: EMAIL → SMTP (MailHog в dev), SMS → внешний шлюз. Заменить на реальные реализации.
 */
@Component
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(String target, OtpChannel channel, OtpPurpose purpose, String code) {
        log.info("OTP [{}] via {} to {} -> {}", purpose, channel, target, code);
    }
}
