package za.co.trademesh.modules.aggregation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DemandGroupSuggestion(
        UUID id,
        UUID requestedByBusinessId,
        UUID anchorOrderId,
        DemandGroupSuggestionStatus status,
        String algorithmVersion,
        String inputFingerprint,
        AggregationThresholds thresholds,
        double score,
        List<DemandOrderEvaluation> orderEvaluations,
        UUID createdByUserId,
        Instant createdAt) {

    public DemandGroupSuggestion {
        orderEvaluations = List.copyOf(orderEvaluations);
    }

    public int includedOrderCount() {
        return (int) orderEvaluations.stream()
                .filter(DemandOrderEvaluation::included)
                .count();
    }
}
