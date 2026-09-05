package greenecomall.auth.otp;

import greenecomall.auth.domain.OtpChannel;
import greenecomall.auth.domain.OtpPurpose;

/**
 * Порт доставки одноразового кода. Реализации: e-mail (SMTP), SMS-шлюз, Telegram.
 */
public interface OtpSender {

    void send(String target, OtpChannel channel, OtpPurpose purpose, String code);
}
