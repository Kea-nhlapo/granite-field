package za.co.trademesh.modules.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalTurnstileVerifierTest {

    @Test
    void bindsATokenToOneActionAndRejectsReplay() {
        LocalTurnstileVerifier verifier = new LocalTurnstileVerifier();
        String token = "local-pass:otp-send:unique";

        assertThat(verifier.verify(token, "127.0.0.1", "otp-send").success()).isTrue();
        assertThat(verifier.verify(token, "127.0.0.1", "otp-send").success()).isFalse();
        assertThat(verifier.verify("local-pass:other:unique", "127.0.0.1", "otp-send")
                        .success())
                .isFalse();
    }
}
