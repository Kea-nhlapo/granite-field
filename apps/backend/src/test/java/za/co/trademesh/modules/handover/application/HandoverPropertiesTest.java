package za.co.trademesh.modules.handover.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HandoverPropertiesTest {

    @Test
    void rejectsUnsafeLimits() {
        assertThatThrownBy(() -> properties(Duration.ZERO, Duration.ZERO, 250, 500))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(Duration.ofMinutes(5), Duration.ofMinutes(2), 0, 500))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(Duration.ofMinutes(5), Duration.ofMinutes(2), 250, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(Duration.ofHours(2), Duration.ofMinutes(2), 250, 500))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(Duration.ofMinutes(5), Duration.ofMinutes(11), 250, 500))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HandoverProperties(
                        Duration.ofMinutes(5), Duration.ofMinutes(2), 250, 500, Duration.ofMinutes(30), "too-short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static HandoverProperties properties(
            Duration challengeTtl, Duration clockSkew, int tolerance, int noteLength) {
        return new HandoverProperties(
                challengeTtl,
                clockSkew,
                tolerance,
                noteLength,
                Duration.ofMinutes(30),
                "test-only-handover-signing-secret-32-characters");
    }
}
