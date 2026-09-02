package za.co.trademesh.modules.supplier.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SupplierInvitationTokens {

    private static final int TOKEN_BYTES = 32;
    private static final int ENCODED_TOKEN_LENGTH = 43;
    private static final Pattern TOKEN_SHAPE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final SecureRandom secureRandom;

    public SupplierInvitationTokens() {
        this(new SecureRandom());
    }

    SupplierInvitationTokens(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public IssuedToken issue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(rawToken, hashUnchecked(rawToken));
    }

    public String hash(String rawToken) {
        if (rawToken == null
                || rawToken.length() != ENCODED_TOKEN_LENGTH
                || !TOKEN_SHAPE.matcher(rawToken).matches()) {
            throw SupplierException.invitationUnavailable();
        }
        return hashUnchecked(rawToken);
    }

    private static String hashUnchecked(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record IssuedToken(String rawToken, String hash) {}
}
