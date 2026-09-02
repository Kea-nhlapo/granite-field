package za.co.trademesh.modules.telemetry.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TelemetryDeviceCredentials {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_SHAPE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedCredential issue(UUID deviceId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedCredential(deviceId + "." + token, hashUnchecked(token));
    }

    public ParsedCredential parse(String rawCredential) {
        if (rawCredential == null) {
            throw TelemetryException.deviceAuthenticationFailed();
        }
        int separator = rawCredential.indexOf('.');
        if (separator < 1 || separator != rawCredential.lastIndexOf('.')) {
            throw TelemetryException.deviceAuthenticationFailed();
        }
        try {
            UUID deviceId = UUID.fromString(rawCredential.substring(0, separator));
            String token = rawCredential.substring(separator + 1);
            if (!TOKEN_SHAPE.matcher(token).matches()) {
                throw TelemetryException.deviceAuthenticationFailed();
            }
            return new ParsedCredential(deviceId, hashUnchecked(token));
        } catch (IllegalArgumentException invalid) {
            throw TelemetryException.deviceAuthenticationFailed();
        }
    }

    public boolean matches(String suppliedHash, String storedHash) {
        return storedHash != null
                && MessageDigest.isEqual(
                        suppliedHash.getBytes(StandardCharsets.US_ASCII),
                        storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hashUnchecked(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record IssuedCredential(String rawCredential, String credentialHash) {}

    public record ParsedCredential(UUID deviceId, String credentialHash) {}
}
