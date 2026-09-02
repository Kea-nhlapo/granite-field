package za.co.trademesh.modules.telemetry.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.telemetry")
public record TelemetryProperties(
        Duration maximumReadingAge,
        Duration futureClockSkew,
        Duration rateLimitWindow,
        int maximumReadingsPerWindow,
        int maximumBatchSize,
        Duration downsampleAfter,
        Duration downsampleBucket,
        Duration retention,
        int cleanupBatchSize,
        Duration cleanupInterval) {

    public TelemetryProperties {
        requirePositive(maximumReadingAge, "maximum-reading-age");
        requireNonNegative(futureClockSkew, "future-clock-skew");
        requirePositive(rateLimitWindow, "rate-limit-window");
        requirePositive(downsampleAfter, "downsample-after");
        requirePositive(downsampleBucket, "downsample-bucket");
        requirePositive(retention, "retention");
        requirePositive(cleanupInterval, "cleanup-interval");
        if (maximumReadingsPerWindow < 1 || maximumBatchSize < 1 || cleanupBatchSize < 1) {
            throw new IllegalArgumentException("Telemetry count limits must be positive");
        }
        if (downsampleBucket.toSeconds() < 1) {
            throw new IllegalArgumentException("Telemetry downsample-bucket must be at least one second");
        }
        if (maximumBatchSize > maximumReadingsPerWindow) {
            throw new IllegalArgumentException("Telemetry batch size cannot exceed the rate limit");
        }
        if (retention.compareTo(downsampleAfter) <= 0) {
            throw new IllegalArgumentException("Telemetry retention must exceed the down-sampling age");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Telemetry " + name + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException("Telemetry " + name + " cannot be negative");
        }
    }
}
