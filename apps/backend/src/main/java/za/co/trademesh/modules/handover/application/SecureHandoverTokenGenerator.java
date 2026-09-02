package za.co.trademesh.modules.handover.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class SecureHandoverTokenGenerator implements HandoverTokenGenerator {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_TOKEN_LENGTH = 1024;

    private final SecureRandom random = new SecureRandom();
    private final byte[] signingKey;

    SecureHandoverTokenGenerator(HandoverProperties properties) {
        this.signingKey = properties.signingSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String generate(TokenClaims claims) {
        byte[] nonce = new byte[24];
        random.nextBytes(nonce);
        String quantity = claims.expectedQuantity() == null
                ? ""
                : claims.expectedQuantity().toPlainString();
        String unit = claims.unitOfMeasure() == null ? "" : claims.unitOfMeasure();
        String payload = String.join(
                "|",
                VERSION,
                claims.challengeId().toString(),
                claims.shipmentId().toString(),
                quantity,
                unit,
                Long.toString(claims.expiresAt().toEpochMilli()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
        String encodedPayload =
                Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "tmh1." + encodedPayload + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    @Override
    public TokenClaims verify(String token) {
        try {
            if (token == null || token.length() > MAX_TOKEN_LENGTH) {
                throw HandoverException.invalidToken();
            }
            String[] tokenParts = token.split("\\.", -1);
            if (tokenParts.length != 3 || !"tmh1".equals(tokenParts[0])) {
                throw HandoverException.invalidToken();
            }
            String payload = new String(Base64.getUrlDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8);
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(tokenParts[2]);
            if (!MessageDigest.isEqual(sign(payload), suppliedSignature)) {
                throw HandoverException.invalidToken();
            }
            String[] fields = payload.split("\\|", -1);
            if (fields.length != 7 || !VERSION.equals(fields[0])) {
                throw HandoverException.invalidToken();
            }
            byte[] nonce = Base64.getUrlDecoder().decode(fields[6]);
            if (nonce.length != 24) {
                throw HandoverException.invalidToken();
            }
            BigDecimal expectedQuantity = fields[3].isEmpty() ? null : new BigDecimal(fields[3]);
            String unit = fields[4].isEmpty() ? null : fields[4];
            if ((expectedQuantity == null) != (unit == null)
                    || (expectedQuantity != null
                            && (expectedQuantity.signum() <= 0
                                    || expectedQuantity.scale() > 4
                                    || !unit.matches("[A-Z_]{1,32}")))) {
                throw HandoverException.invalidToken();
            }
            return new TokenClaims(
                    UUID.fromString(fields[1]),
                    UUID.fromString(fields[2]),
                    expectedQuantity,
                    unit,
                    Instant.ofEpochMilli(Long.parseLong(fields[5])));
        } catch (HandoverException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw HandoverException.invalidToken();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", impossible);
        }
    }
}
