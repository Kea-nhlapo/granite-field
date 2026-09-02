package za.co.trademesh.modules.notification.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.notification.application.NotificationDataProtectionProperties;
import za.co.trademesh.modules.notification.application.NotificationDataProtector;

@Component
public class SensitiveNotificationDataProtector implements NotificationDataProtector {

    private static final String VERSION = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SensitiveNotificationDataProtector(NotificationDataProtectionProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.dataEncryptionKey());
        } catch (IllegalArgumentException invalidEncoding) {
            throw new IllegalStateException("Notification data encryption key must be Base64", invalidEncoding);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("Notification data encryption key must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    @Override
    public String protect(String plainText) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return VERSION
                    + Base64.getEncoder()
                            .encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                                    .put(iv)
                                    .put(encrypted)
                                    .array());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not protect notification data", failure);
        }
    }

    @Override
    public String unprotect(String protectedText) {
        if (protectedText == null || !protectedText.startsWith(VERSION)) {
            throw new IllegalStateException("Unsupported protected notification data");
        }
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(protectedText.substring(VERSION.length()));
        } catch (IllegalArgumentException invalidEncoding) {
            throw new IllegalStateException("Protected notification data is invalid", invalidEncoding);
        }
        if (combined.length <= IV_BYTES) {
            throw new IllegalStateException("Protected notification data is invalid");
        }
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
        byte[] encrypted = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not unprotect notification data", failure);
        }
    }

    @Override
    public String fingerprint(String plainText) {
        if (plainText == null) {
            throw new IllegalArgumentException("Cannot fingerprint null notification data");
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not fingerprint notification data", failure);
        }
    }
}
