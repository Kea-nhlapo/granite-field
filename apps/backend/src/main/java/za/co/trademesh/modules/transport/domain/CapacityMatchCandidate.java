package za.co.trademesh.modules.transport.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CapacityMatchCandidate(
        UUID offerId,
        UUID transporterId,
        boolean compatible,
        Integer rank,
        Capacity availableCapacity,
        double addedDistanceMetres,
        long timingOverlapSeconds,
        BigDecimal estimatedCostZar,
        double score,
        List<CapacityConstraintResult> constraintResults,
        List<CapacityScoreComponent> scoreComponents) {

    public CapacityMatchCandidate {
        constraintResults = List.copyOf(constraintResults);
        scoreComponents = List.copyOf(scoreComponents);
    }
}
