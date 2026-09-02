package za.co.trademesh.modules.aggregation.domain;

import java.util.List;
import java.util.UUID;

public record DemandOrderEvaluation(
        UUID orderId,
        UUID buyerBusinessId,
        AggregationOrderRole role,
        boolean included,
        String destinationLabel,
        double distanceMeters,
        long windowOverlapSeconds,
        double cargoOverlapRatio,
        double score,
        List<AggregationConstraintResult> constraintResults) {

    public DemandOrderEvaluation {
        constraintResults = List.copyOf(constraintResults);
    }
}
