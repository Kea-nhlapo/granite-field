package za.co.trademesh.modules.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.notification.application.InfobipNotificationProperties;

class InfobipWebhookVerifierTest {

    private static final String SECRET = "test-only-hmac-secret";
    private final InfobipWebhookVerifier verifier = new InfobipWebhookVerifier(
            new InfobipNotificationProperties("", "", "", "", SECRET, java.util.Map.of(), null, null));

    @Test
    void verifiesTheExactRawBodyAndRejectsAnyMutation() throws Exception {
        byte[] body = "{\"messageId\":\"message-1\"}\n".getBytes(StandardCharsets.UTF_8);
        String signature = HexFormat.of().formatHex(hmac(body));

        assertThat(verifier.valid(body, signature)).isTrue();
        assertThat(verifier.valid(body, "sha256=" + signature)).isTrue();
        assertThat(verifier.valid("{\"messageId\":\"message-1\"}".getBytes(StandardCharsets.UTF_8), signature))
                .isFalse();
        assertThat(verifier.valid(body, "invalid-signature")).isFalse();
        assertThat(verifier.fingerprint(body, "delivery", 0)).matches("[0-9a-f]{64}");
        assertThat(verifier.fingerprint(body, "delivery", 0))
                .isNotEqualTo(verifier.fingerprint(body, "delivery", 1))
                .isNotEqualTo(verifier.fingerprint(body, "seen", 0));
    }

    private static byte[] hmac(byte[] body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return hmac.doFinal(body);
    }
}
