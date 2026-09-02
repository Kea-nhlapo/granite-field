package za.co.trademesh.modules.telemetry.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TelemetryPropertiesTest {

    @Test
    void rejectsRetentionThatCannotContainTheDownsampledWindow() {
        assertThatThrownBy(() -> new TelemetryProperties(
                        Duration.ofDays(7),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(1),
                        600,
                        100,
                        Duration.ofDays(7),
                        Duration.ofMinutes(5),
                        Duration.ofDays(7),
                        5000,
                        Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retention");
    }
}
