package greenecomall.mlm.integration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Подпись тел HTTP-вызовов между маркетплейсом и MLM-бэком в обе стороны:
 * {@code X-Signature = hex(HMAC-SHA256(secret, X-Timestamp + "." + body))}.
 */
public final class HmacSigner {

    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private HmacSigner() {
    }

    public static String sign(String secret, String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("cannot compute HMAC", e);
        }
    }

    public static boolean verify(String secret, String timestamp, String body, String signature) {
        if (timestamp == null || body == null || signature == null) {
            return false;
        }
        byte[] expected = sign(secret, timestamp, body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
