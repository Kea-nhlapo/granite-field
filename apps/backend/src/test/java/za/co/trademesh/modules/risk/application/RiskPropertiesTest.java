package za.co.trademesh.modules.risk.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RiskPropertiesTest {

    @Test
    void rejectsUnsafeThresholdConfiguration() {
        assertThatThrownBy(() -> properties(0, 2, Duration.ofMinutes(20))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(1000, 0, Duration.ofMinutes(20)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(1000, 2, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private static RiskProperties properties(double routeMetres, int confirmations, Duration stopDuration) {
        return new RiskProperties(
                routeMetres,
                confirmations,
                new BigDecimal("2"),
                stopDuration,
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                new BigDecimal("30"),
                Duration.ofMinutes(20),
                Duration.ofMinutes(30),
                Duration.ofMinutes(1),
                100,
                1000,
                "operational-risk/v1");
    }
}
