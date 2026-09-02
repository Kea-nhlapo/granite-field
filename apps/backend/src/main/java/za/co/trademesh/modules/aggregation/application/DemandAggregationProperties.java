package za.co.trademesh.modules.aggregation.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.aggregation")
public record DemandAggregationProperties(
        double searchRadiusMeters,
        double maximumDistanceMeters,
        Duration minimumWindowOverlap,
        double minimumCargoOverlapRatio,
        int candidateLimit,
        double distanceWeight,
        double windowWeight,
        double cargoWeight,
        String algorithmVersion) {

    public DemandAggregationProperties {
        if (maximumDistanceMeters <= 0) {
            maximumDistanceMeters = 15_000;
        }
        if (searchRadiusMeters < maximumDistanceMeters) {
            searchRadiusMeters = Math.max(30_000, maximumDistanceMeters);
        }
        if (minimumWindowOverlap == null || minimumWindowOverlap.isNegative()) {
            minimumWindowOverlap = Duration.ofMinutes(30);
        }
        if (minimumCargoOverlapRatio <= 0 || minimumCargoOverlapRatio > 1) {
            minimumCargoOverlapRatio = 0.25;
        }
        if (candidateLimit <= 0) {
            candidateLimit = 100;
        }
        if (distanceWeight < 0 || windowWeight < 0 || cargoWeight < 0) {
            distanceWeight = 0.35;
            windowWeight = 0.25;
            cargoWeight = 0.40;
        }
        if (distanceWeight + windowWeight + cargoWeight == 0) {
            distanceWeight = 0.35;
            windowWeight = 0.25;
            cargoWeight = 0.40;
        }
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            algorithmVersion = "demand-group/v1";
        }
    }
}
