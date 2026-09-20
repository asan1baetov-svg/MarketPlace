package greenecomall.finance.finik;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Подпись Finik: RSA-SHA256 над каноничной строкой
 * <pre>
 * &lt;method lower&gt;\n&lt;path&gt;\nhost:&lt;host&gt;&amp;x-api-*:&lt;value&gt;...\n&lt;body&gt;
 * </pre>
 * заголовки {@code x-api-*} — по алфавиту. Перенесено из MLM-системы GreenEcoMall ({@code FinikSignatureUtil}).
 */
public final class FinikSigner {

    private FinikSigner() {
    }

    public static String canonical(String method, String path, String host, Map<String, String> apiHeaders, String body) {
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(apiHeaders);
        String headers = sorted.entrySet().stream()
                .map(e -> e.getKey().toLowerCase() + ":" + e.getValue())
                .collect(Collectors.joining("&"));
        return method.toLowerCase() + "\n" + path + "\n" + "host:" + host + (headers.isEmpty() ? "" : "&" + headers)
                + "\n" + (body == null ? "" : body);
    }

    public static String sign(String canonical, PrivateKey key) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key);
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("cannot sign Finik request", e);
        }
    }

    public static boolean verify(String canonical, String signatureBase64, PublicKey key) {
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signatureBase64.trim()));
        } catch (Exception e) {
            return false;
        }
    }

    /** PEM в формате PKCS#8 ({@code BEGIN PRIVATE KEY}) или PKCS#1 ({@code BEGIN RSA PRIVATE KEY}). */
    public static PrivateKey privateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Finik private key is not configured");
        }
        try {
            boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
            byte[] der = Base64.getDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", ""));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? pkcs1ToPkcs8(der) : der));
        } catch (Exception e) {
            throw new IllegalStateException("invalid Finik private key", e);
        }
    }

    public static PublicKey publicKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", ""));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("invalid Finik public key", e);
        }
    }

    /** Оборачивает PKCS#1 RSAPrivateKey в PKCS#8 PrivateKeyInfo (заголовок фиксированной длины). */
    private static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        int len = pkcs1.length;
        int total = len + 22;
        byte[] header = {
                0x30, (byte) 0x82, (byte) ((total >> 8) & 0xff), (byte) (total & 0xff),
                0x2, 0x1, 0x0,
                0x30, 0xD, 0x6, 0x9, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0xD, 0x1, 0x1, 0x1, 0x5, 0x0,
                0x4, (byte) 0x82, (byte) ((len >> 8) & 0xff), (byte) (len & 0xff)
        };
        byte[] out = new byte[header.length + len];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(pkcs1, 0, out, header.length, len);
        return out;
    }
}
