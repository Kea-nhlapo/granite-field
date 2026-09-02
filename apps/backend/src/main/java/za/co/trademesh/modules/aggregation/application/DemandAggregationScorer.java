package za.co.trademesh.modules.aggregation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
class DemandAggregationScorer {

    private final DemandAggregationProperties properties;

    DemandAggregationScorer(DemandAggregationProperties properties) {
        this.properties = properties;
    }

    double score(double distanceMeters, double windowOverlapRatio, double cargoOverlapRatio) {
        double distancePreference = clamp(1 - (distanceMeters / properties.maximumDistanceMeters()));
        double weightTotal = properties.distanceWeight() + properties.windowWeight() + properties.cargoWeight();
        double weighted = (distancePreference * properties.distanceWeight()
                        + clamp(windowOverlapRatio) * properties.windowWeight()
                        + clamp(cargoOverlapRatio) * properties.cargoWeight())
                / weightTotal;
        return BigDecimal.valueOf(clamp(weighted))
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
