package za.co.trademesh.modules.aggregation.api;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.aggregation.application.DemandAggregationService;
import za.co.trademesh.modules.aggregation.domain.AggregationConstraint;
import za.co.trademesh.modules.aggregation.domain.AggregationConstraintResult;
import za.co.trademesh.modules.aggregation.domain.AggregationExclusionReason;
import za.co.trademesh.modules.aggregation.domain.AggregationOrderRole;
import za.co.trademesh.modules.aggregation.domain.AggregationThresholds;
import za.co.trademesh.modules.aggregation.domain.ConstraintOutcome;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestion;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestionStatus;
import za.co.trademesh.modules.aggregation.domain.DemandOrderEvaluation;

public final class DemandAggregationContracts {

    private DemandAggregationContracts() {}

    public record SuggestDemandGroupRequest(
            @NotNull UUID requestId, @NotNull UUID anchorOrderId) {
        DemandAggregationService.SuggestDemandGroup toCommand() {
            return new DemandAggregationService.SuggestDemandGroup(requestId, anchorOrderId);
        }
    }

    public record SuggestionResponse(
            UUID suggestionId,
            UUID requestedByBusinessId,
            UUID anchorOrderId,
            DemandGroupSuggestionStatus status,
            String algorithmVersion,
            ThresholdResponse thresholds,
            double score,
            int includedOrderCount,
            List<OrderEvaluationResponse> orders,
            Instant createdAt) {

        static SuggestionResponse from(DemandGroupSuggestion suggestion) {
            return new SuggestionResponse(
                    suggestion.id(),
                    suggestion.requestedByBusinessId(),
                    suggestion.anchorOrderId(),
                    suggestion.status(),
                    suggestion.algorithmVersion(),
                    ThresholdResponse.from(suggestion.thresholds()),
                    suggestion.score(),
                    suggestion.includedOrderCount(),
                    suggestion.orderEvaluations().stream()
                            .map(OrderEvaluationResponse::from)
                            .toList(),
                    suggestion.createdAt());
        }
    }

    public record ThresholdResponse(
            double searchRadiusMeters,
            double maximumDistanceMeters,
            Duration minimumWindowOverlap,
            double minimumCargoOverlapRatio,
            int candidateLimit) {

        static ThresholdResponse from(AggregationThresholds thresholds) {
            return new ThresholdResponse(
                    thresholds.searchRadiusMeters(),
                    thresholds.maximumDistanceMeters(),
                    thresholds.minimumWindowOverlap(),
                    thresholds.minimumCargoOverlapRatio(),
                    thresholds.candidateLimit());
        }
    }

    public record OrderEvaluationResponse(
            UUID orderId,
            AggregationOrderRole role,
            boolean included,
            double distanceMeters,
            long windowOverlapSeconds,
            double cargoOverlapRatio,
            double score,
            List<AggregationConstraint> passedChecks,
            List<AggregationExclusionReason> exclusionReasons,
            List<ConstraintResponse> checks) {

        static OrderEvaluationResponse from(DemandOrderEvaluation evaluation) {
            return new OrderEvaluationResponse(
                    evaluation.orderId(),
                    evaluation.role(),
                    evaluation.included(),
                    evaluation.distanceMeters(),
                    evaluation.windowOverlapSeconds(),
                    evaluation.cargoOverlapRatio(),
                    evaluation.score(),
                    evaluation.constraintResults().stream()
                            .filter(result -> result.outcome() == ConstraintOutcome.PASS)
                            .map(AggregationConstraintResult::constraint)
                            .toList(),
                    evaluation.constraintResults().stream()
                            .filter(result -> result.outcome() == ConstraintOutcome.FAIL)
                            .map(AggregationConstraintResult::exclusionReason)
                            .toList(),
                    evaluation.constraintResults().stream()
                            .map(ConstraintResponse::from)
                            .toList());
        }
    }

    public record ConstraintResponse(
            AggregationConstraint constraint,
            ConstraintOutcome outcome,
            AggregationExclusionReason exclusionReason,
            String explanation) {

        static ConstraintResponse from(AggregationConstraintResult result) {
            return new ConstraintResponse(
                    result.constraint(), result.outcome(), result.exclusionReason(), result.explanation());
        }
    }
}
