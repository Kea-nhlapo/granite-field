package za.co.trademesh.modules.handover.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecureHandoverTokenGeneratorTest {

    private final SecureHandoverTokenGenerator tokens = new SecureHandoverTokenGenerator(new HandoverProperties(
            Duration.ofMinutes(5),
            Duration.ofMinutes(2),
            250,
            500,
            Duration.ofMinutes(30),
            "test-only-handover-signing-secret-32-characters"));

    @Test
    void signsAndVerifiesExpectedDeliveryClaims() {
        var claims = new HandoverTokenGenerator.TokenClaims(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("20.0000"),
                "CASE",
                Instant.parse("2026-09-02T08:05:00Z"));

        String token = tokens.generate(claims);

        assertThat(token).startsWith("tmh1.");
        assertThat(tokens.verify(token)).isEqualTo(claims);
    }

    @Test
    void rejectsAChangedPayloadOrSignature() {
        var claims = new HandoverTokenGenerator.TokenClaims(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("20.0000"),
                "CASE",
                Instant.parse("2026-09-02T08:05:00Z"));
        String token = tokens.generate(claims);
        int payloadStart = token.indexOf('.') + 1;
        char replacement = token.charAt(payloadStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, payloadStart) + replacement + token.substring(payloadStart + 1);

        assertThatThrownBy(() -> tokens.verify(tampered))
                .isInstanceOf(HandoverException.class)
                .hasMessageContaining("unavailable");
    }
}
