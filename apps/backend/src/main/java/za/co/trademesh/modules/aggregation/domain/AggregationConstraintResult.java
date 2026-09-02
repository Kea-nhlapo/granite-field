package za.co.trademesh.modules.aggregation.domain;

public record AggregationConstraintResult(
        AggregationConstraint constraint,
        ConstraintOutcome outcome,
        AggregationExclusionReason exclusionReason,
        String explanation) {}
