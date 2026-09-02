package za.co.trademesh.modules.risk.application;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.risk")
public record RiskProperties(
        double routeDeviationMetres,
        int routeDeviationConfirmations,
        BigDecimal stoppedSpeedKph,
        Duration unexpectedStopDuration,
        Duration maximumTelemetryGap,
        Duration trackerOfflineDuration,
        BigDecimal fuelDropLitres,
        Duration fuelDropWindow,
        Duration deliveryGrace,
        Duration scanInterval,
        int scanBatchSize,
        int readingWindowLimit,
        String ruleVersion) {

    public RiskProperties {
        if (!Double.isFinite(routeDeviationMetres) || routeDeviationMetres <= 0) {
            throw new IllegalArgumentException("Risk route-deviation-metres must be positive");
        }
        if (routeDeviationConfirmations < 1 || scanBatchSize < 1 || readingWindowLimit < 2) {
            throw new IllegalArgumentException("Risk count limits are invalid");
        }
        requireNonNegative(stoppedSpeedKph, "stopped-speed-kph");
        requirePositive(fuelDropLitres, "fuel-drop-litres");
        requirePositive(unexpectedStopDuration, "unexpected-stop-duration");
        requirePositive(maximumTelemetryGap, "maximum-telemetry-gap");
        requirePositive(trackerOfflineDuration, "tracker-offline-duration");
        requirePositive(fuelDropWindow, "fuel-drop-window");
        requireNonNegative(deliveryGrace, "delivery-grace");
        requirePositive(scanInterval, "scan-interval");
        if (ruleVersion == null || ruleVersion.isBlank() || ruleVersion.length() > 64) {
            throw new IllegalArgumentException("Risk rule-version is invalid");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Risk " + name + " must be positive");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Risk " + name + " cannot be negative");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Risk " + name + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException("Risk " + name + " cannot be negative");
        }
    }
}
