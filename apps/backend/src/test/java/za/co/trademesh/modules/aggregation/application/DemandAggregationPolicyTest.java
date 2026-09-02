package za.co.trademesh.modules.aggregation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.aggregation.domain.AggregationExclusionReason;
import za.co.trademesh.modules.procurement.application.AggregationOrderCatalog.CargoItem;
import za.co.trademesh.modules.procurement.application.AggregationOrderCatalog.OrderCandidate;

class DemandAggregationPolicyTest {

    private static final UUID SUPPLIER = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-09-05T08:00:00Z");
    private static final DemandAggregationProperties PROPERTIES = new DemandAggregationProperties(
            30_000, 15_000, Duration.ofMinutes(30), 0.25, 100, 0.35, 0.25, 0.40, "demand-group/v1");

    private final DemandAggregationConstraints constraints = new DemandAggregationConstraints(PROPERTIES);
    private final DemandAggregationScorer scorer = new DemandAggregationScorer(PROPERTIES);

    @Test
    void keepsHardConstraintsIndependentFromPreferenceScoring() {
        OrderCandidate anchor = candidate(SUPPLIER, 0, START, START.plus(Duration.ofHours(4)), "DRINK-001");
        OrderCandidate eligible = candidate(
                SUPPLIER, 5_000, START.plus(Duration.ofHours(1)), START.plus(Duration.ofHours(5)), "DRINK-001");
        OrderCandidate wrongSupplier =
                candidate(UUID.randomUUID(), 500, START, START.plus(Duration.ofHours(4)), "DRINK-001");

        var eligibleAssessment = constraints.evaluate(anchor, eligible);
        var excludedAssessment = constraints.evaluate(anchor, wrongSupplier);

        assertThat(eligibleAssessment.included()).isTrue();
        assertThat(excludedAssessment.included()).isFalse();
        assertThat(excludedAssessment.constraintResults())
                .extracting(result -> result.exclusionReason())
                .contains(AggregationExclusionReason.SUPPLIER_OR_PICKUP_MISMATCH);
        assertThat(scorer.score(500, 1, 1)).isGreaterThan(scorer.score(5_000, 0.75, 1));
    }

    @Test
    void reportsEachFailedConstraintWithoutCargoSpecificBranches() {
        OrderCandidate anchor = candidate(SUPPLIER, 0, START, START.plus(Duration.ofHours(2)), "DRINK-001");
        OrderCandidate excluded = candidate(
                SUPPLIER, 20_000, START.plus(Duration.ofHours(3)), START.plus(Duration.ofHours(4)), "MEAL-001");

        var assessment = constraints.evaluate(anchor, excluded);

        assertThat(assessment.included()).isFalse();
        assertThat(assessment.constraintResults())
                .extracting(result -> result.exclusionReason())
                .contains(
                        AggregationExclusionReason.DISTANCE_EXCEEDS_LIMIT,
                        AggregationExclusionReason.DELIVERY_WINDOWS_DO_NOT_OVERLAP,
                        AggregationExclusionReason.CARGO_NOT_COMPATIBLE);
    }

    @Test
    void failsClosedWhenConfirmedCargoCodesAreUnavailable() {
        OrderCandidate anchor = candidate(SUPPLIER, 0, START, START.plus(Duration.ofHours(2)), null);
        OrderCandidate candidate = candidate(SUPPLIER, 500, START, START.plus(Duration.ofHours(2)), "DRINK-001");

        var assessment = constraints.evaluate(anchor, candidate);

        assertThat(assessment.included()).isFalse();
        assertThat(assessment.constraintResults())
                .extracting(result -> result.exclusionReason())
                .contains(AggregationExclusionReason.CARGO_PROFILE_UNAVAILABLE);
    }

    private static OrderCandidate candidate(
            UUID supplier, double distance, Instant windowStart, Instant windowEnd, String productCode) {
        return new OrderCandidate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                supplier,
                "Destination",
                -25.99,
                28.22,
                distance,
                windowStart,
                windowEnd,
                List.of(new CargoItem(productCode, "CASE")));
    }
}
