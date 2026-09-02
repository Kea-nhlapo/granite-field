package za.co.trademesh.modules.aggregation.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.aggregation.domain.AggregationConstraint;
import za.co.trademesh.modules.aggregation.domain.AggregationConstraintResult;
import za.co.trademesh.modules.aggregation.domain.AggregationExclusionReason;
import za.co.trademesh.modules.aggregation.domain.ConstraintOutcome;
import za.co.trademesh.modules.procurement.application.AggregationOrderCatalog.OrderCandidate;

@Component
class DemandAggregationConstraints {

    private final DemandAggregationProperties properties;

    DemandAggregationConstraints(DemandAggregationProperties properties) {
        this.properties = properties;
    }

    Assessment evaluate(OrderCandidate anchor, OrderCandidate candidate) {
        long overlapSeconds = overlapSeconds(anchor, candidate);
        double overlapRatio = windowOverlapRatio(anchor, candidate, overlapSeconds);
        CargoComparison cargo = cargoComparison(anchor, candidate);

        List<AggregationConstraintResult> checks = List.of(
                supplierCheck(anchor, candidate),
                distanceCheck(candidate.distanceFromAnchorMeters()),
                windowCheck(overlapSeconds),
                cargoCheck(cargo));
        boolean included = checks.stream().allMatch(result -> result.outcome() == ConstraintOutcome.PASS);
        return new Assessment(included, overlapSeconds, overlapRatio, cargo.ratio(), checks);
    }

    private AggregationConstraintResult supplierCheck(OrderCandidate anchor, OrderCandidate candidate) {
        boolean matches = anchor.supplierProfileId().equals(candidate.supplierProfileId());
        return result(
                AggregationConstraint.SUPPLIER_OR_PICKUP_COMPATIBLE,
                matches,
                AggregationExclusionReason.SUPPLIER_OR_PICKUP_MISMATCH,
                matches
                        ? "The orders use the same supplier, so their pickup is compatible for this MVP."
                        : "The supplier or pickup source does not match the anchor order.");
    }

    private AggregationConstraintResult distanceCheck(double distanceMeters) {
        boolean matches = distanceMeters <= properties.maximumDistanceMeters();
        return result(
                AggregationConstraint.WITHIN_DISTANCE,
                matches,
                AggregationExclusionReason.DISTANCE_EXCEEDS_LIMIT,
                matches
                        ? "The destination is within the configured consolidation distance."
                        : "The destination is outside the configured consolidation distance.");
    }

    private AggregationConstraintResult windowCheck(long overlapSeconds) {
        boolean matches = overlapSeconds >= properties.minimumWindowOverlap().toSeconds();
        return result(
                AggregationConstraint.DELIVERY_WINDOW_OVERLAP,
                matches,
                AggregationExclusionReason.DELIVERY_WINDOWS_DO_NOT_OVERLAP,
                matches
                        ? "The delivery windows overlap for long enough."
                        : "The delivery windows do not meet the configured minimum overlap.");
    }

    private AggregationConstraintResult cargoCheck(CargoComparison cargo) {
        if (!cargo.available()) {
            return new AggregationConstraintResult(
                    AggregationConstraint.CARGO_COMPATIBLE,
                    ConstraintOutcome.FAIL,
                    AggregationExclusionReason.CARGO_PROFILE_UNAVAILABLE,
                    "A confirmed product code is required on both orders before cargo compatibility can be checked.");
        }
        boolean matches = cargo.ratio() >= properties.minimumCargoOverlapRatio();
        return result(
                AggregationConstraint.CARGO_COMPATIBLE,
                matches,
                AggregationExclusionReason.CARGO_NOT_COMPATIBLE,
                matches
                        ? "The confirmed product-code profiles meet the configured overlap threshold."
                        : "The confirmed product-code profiles do not meet the configured overlap threshold.");
    }

    private static AggregationConstraintResult result(
            AggregationConstraint constraint,
            boolean passed,
            AggregationExclusionReason failureReason,
            String explanation) {
        return new AggregationConstraintResult(
                constraint,
                passed ? ConstraintOutcome.PASS : ConstraintOutcome.FAIL,
                passed ? null : failureReason,
                explanation);
    }

    private static long overlapSeconds(OrderCandidate left, OrderCandidate right) {
        Instant start = left.deliveryWindowStart().isAfter(right.deliveryWindowStart())
                ? left.deliveryWindowStart()
                : right.deliveryWindowStart();
        Instant end = left.deliveryWindowEnd().isBefore(right.deliveryWindowEnd())
                ? left.deliveryWindowEnd()
                : right.deliveryWindowEnd();
        return Math.max(0, Duration.between(start, end).toSeconds());
    }

    private static double windowOverlapRatio(OrderCandidate anchor, OrderCandidate candidate, long overlapSeconds) {
        long longestWindow = Math.max(
                Duration.between(anchor.deliveryWindowStart(), anchor.deliveryWindowEnd())
                        .toSeconds(),
                Duration.between(candidate.deliveryWindowStart(), candidate.deliveryWindowEnd())
                        .toSeconds());
        return longestWindow == 0 ? 0 : clamp((double) overlapSeconds / longestWindow);
    }

    private static CargoComparison cargoComparison(OrderCandidate anchor, OrderCandidate candidate) {
        Set<String> anchorCodes = cargoCodes(anchor);
        Set<String> candidateCodes = cargoCodes(candidate);
        if (anchorCodes.isEmpty() || candidateCodes.isEmpty()) {
            return new CargoComparison(false, 0);
        }
        Set<String> shared = new HashSet<>(anchorCodes);
        shared.retainAll(candidateCodes);
        double overlap = (double) shared.size() / Math.min(anchorCodes.size(), candidateCodes.size());
        return new CargoComparison(true, clamp(overlap));
    }

    private static Set<String> cargoCodes(OrderCandidate order) {
        Set<String> result = new HashSet<>();
        order.cargoItems().stream()
                .map(item -> item.productCode())
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.strip().toUpperCase(Locale.ROOT))
                .forEach(result::add);
        return result;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    record Assessment(
            boolean included,
            long windowOverlapSeconds,
            double windowOverlapRatio,
            double cargoOverlapRatio,
            List<AggregationConstraintResult> constraintResults) {

        Assessment {
            constraintResults = List.copyOf(constraintResults);
        }
    }

    private record CargoComparison(boolean available, double ratio) {}
}
