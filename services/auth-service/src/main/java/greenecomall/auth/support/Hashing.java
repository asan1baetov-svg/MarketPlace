package greenecomall.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 в hex (64 символа) — для хранения хэшей refresh-токенов и OTP-кодов.
 * Это не для паролей (там bcrypt/argon2) — здесь значения одноразовые/короткоживущие.
 */
public final class Hashing {

    private Hashing() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
