package za.co.trademesh.modules.trust.application;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.trust.scores")
public record TrustScoreProperties(
        Duration verifiedInterval,
        Duration evidenceHalfLife,
        BigDecimal maximumVerifiedMovement,
        int verificationBatchSize,
        String verificationScheduleMode,
        String calculationVersion) {

    public TrustScoreProperties {
        requirePositive(verifiedInterval, "verified-interval");
        requirePositive(evidenceHalfLife, "evidence-half-life");
        if (maximumVerifiedMovement == null
                || maximumVerifiedMovement.signum() <= 0
                || maximumVerifiedMovement.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Trust maximum-verified-movement must be between 0 and 100");
        }
        if (verificationBatchSize < 1) {
            throw new IllegalArgumentException("Trust verification-batch-size must be positive");
        }
        if (verificationScheduleMode == null || verificationScheduleMode.isBlank()) {
            throw new IllegalArgumentException("Trust verification-schedule-mode is required");
        }
        if (calculationVersion == null || calculationVersion.isBlank()) {
            throw new IllegalArgumentException("Trust calculation-version is required");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Trust " + name + " must be positive");
        }
    }
}
