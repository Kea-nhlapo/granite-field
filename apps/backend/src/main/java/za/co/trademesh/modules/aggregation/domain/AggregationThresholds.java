package za.co.trademesh.modules.aggregation.domain;

import java.time.Duration;

public record AggregationThresholds(
        double searchRadiusMeters,
        double maximumDistanceMeters,
        Duration minimumWindowOverlap,
        double minimumCargoOverlapRatio,
        int candidateLimit) {}
