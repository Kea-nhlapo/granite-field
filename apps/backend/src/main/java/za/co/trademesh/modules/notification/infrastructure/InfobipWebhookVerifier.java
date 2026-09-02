package za.co.trademesh.modules.notification.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.notification.application.InfobipNotificationProperties;

@Component
public class InfobipWebhookVerifier {

    private final byte[] secret;

    public InfobipWebhookVerifier(InfobipNotificationProperties properties) {
        this.secret = properties.webhookHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    public boolean valid(byte[] rawBody, String suppliedSignature) {
        if (secret.length == 0 || rawBody == null || suppliedSignature == null || suppliedSignature.isBlank()) {
            return false;
        }
        byte[] supplied = decode(suppliedSignature.strip());
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(hmac(rawBody), supplied);
    }

    public String fingerprint(byte[] rawBody, String reportType, int resultIndex) {
        if (rawBody == null || reportType == null || reportType.isBlank() || resultIndex < 0) {
            throw new IllegalArgumentException("Invalid callback fingerprint input");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(reportType.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(rawBody);
            digest.update((byte) ':');
            digest.update(Integer.toString(resultIndex).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    private byte[] hmac(byte[] rawBody) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return hmac.doFinal(rawBody);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA-256 must be available", impossible);
        }
    }

    private static byte[] decode(String signature) {
        String value = signature.regionMatches(true, 0, "sha256=", 0, 7) ? signature.substring(7) : signature;
        try {
            if (value.matches("(?i)[0-9a-f]{64}")) {
                return HexFormat.of().parseHex(value);
            }
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
