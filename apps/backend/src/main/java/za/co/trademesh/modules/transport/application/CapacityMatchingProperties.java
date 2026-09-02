package za.co.trademesh.modules.transport.application;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.capacity-matching")
public record CapacityMatchingProperties(
        int candidateLimit,
        double distanceWeight,
        double capacityFitWeight,
        double costWeight,
        double timingWeight,
        double maximumAddedDistanceMetres,
        BigDecimal estimatedCostPerKilometreZar,
        Duration reservationTtl,
        int expiryBatchSize,
        String algorithmVersion) {}
