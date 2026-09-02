package za.co.trademesh.modules.aggregation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.aggregation.domain.AggregationConstraint;
import za.co.trademesh.modules.aggregation.domain.AggregationConstraintResult;
import za.co.trademesh.modules.aggregation.domain.AggregationOrderRole;
import za.co.trademesh.modules.aggregation.domain.AggregationThresholds;
import za.co.trademesh.modules.aggregation.domain.ConstraintOutcome;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestion;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestionRepository;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestionStatus;
import za.co.trademesh.modules.aggregation.domain.DemandOrderEvaluation;
import za.co.trademesh.modules.aggregation.events.AggregationEvent;
import za.co.trademesh.modules.procurement.application.AggregationOrderCatalog;
import za.co.trademesh.modules.procurement.application.AggregationOrderCatalog.OrderCandidate;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class DemandAggregationService {

    private final AggregationOrderCatalog orders;
    private final DemandGroupSuggestionRepository suggestions;
    private final DemandAggregationConstraints constraints;
    private final DemandAggregationScorer scorer;
    private final DemandAggregationProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public DemandAggregationService(
            AggregationOrderCatalog orders,
            DemandGroupSuggestionRepository suggestions,
            DemandAggregationConstraints constraints,
            DemandAggregationScorer scorer,
            DemandAggregationProperties properties,
            DomainEvents events,
            Clock clock) {
        this.orders = orders;
        this.suggestions = suggestions;
        this.constraints = constraints;
        this.scorer = scorer;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public DemandGroupSuggestion suggest(UUID businessId, SuggestDemandGroup command, UUID actorUserId) {
        if (command == null || command.requestId() == null || command.anchorOrderId() == null) {
            throw DemandAggregationException.invalidRequest();
        }
        var existingRequest = suggestions.findByClientRequestId(businessId, command.requestId());
        if (existingRequest.isPresent()) {
            return sameAnchor(existingRequest.get(), command.anchorOrderId());
        }

        OrderCandidate anchor = orders.findConfirmedOrder(businessId, command.anchorOrderId())
                .orElseThrow(DemandAggregationException::orderNotFound);
        List<OrderCandidate> candidates = orders.findNearbyConfirmedOrders(
                anchor.orderId(), properties.searchRadiusMeters(), properties.candidateLimit());
        List<DemandOrderEvaluation> evaluations = evaluate(anchor, candidates);
        List<UUID> includedOrderIds = evaluations.stream()
                .filter(DemandOrderEvaluation::included)
                .map(DemandOrderEvaluation::orderId)
                .sorted()
                .toList();
        String fingerprint = fingerprint(includedOrderIds, properties.algorithmVersion());
        DemandGroupSuggestionStatus status =
                includedOrderIds.size() > 1 ? DemandGroupSuggestionStatus.ACTIVE : DemandGroupSuggestionStatus.NO_MATCH;
        if (status == DemandGroupSuggestionStatus.ACTIVE) {
            var existingGroup = suggestions.findActiveByFingerprint(businessId, fingerprint);
            if (existingGroup.isPresent()) {
                return existingGroup.get();
            }
        }

        Instant now = clock.instant();
        DemandGroupSuggestion suggestion = new DemandGroupSuggestion(
                UUID.randomUUID(),
                businessId,
                anchor.orderId(),
                status,
                properties.algorithmVersion(),
                fingerprint,
                thresholds(),
                groupScore(evaluations),
                evaluations,
                actorUserId,
                now);
        if (!suggestions.save(suggestion, command.requestId())) {
            var concurrentRequest = suggestions.findByClientRequestId(businessId, command.requestId());
            if (concurrentRequest.isPresent()) {
                return sameAnchor(concurrentRequest.get(), command.anchorOrderId());
            }
            if (status == DemandGroupSuggestionStatus.ACTIVE) {
                return suggestions
                        .findActiveByFingerprint(businessId, fingerprint)
                        .orElseThrow(DemandAggregationException::idempotencyConflict);
            }
            throw DemandAggregationException.idempotencyConflict();
        }
        events.publish(
                new AggregationEvent.SuggestionCreated(
                        suggestion.id(),
                        businessId,
                        anchor.orderId(),
                        suggestion.status().name(),
                        suggestion.includedOrderCount()),
                actorUserId.toString());
        return suggestion;
    }

    @Transactional(readOnly = true)
    public DemandGroupSuggestion get(UUID businessId, UUID suggestionId) {
        return suggestions
                .findById(businessId, suggestionId)
                .orElseThrow(DemandAggregationException::suggestionNotFound);
    }

    private List<DemandOrderEvaluation> evaluate(OrderCandidate anchor, List<OrderCandidate> candidates) {
        List<DemandOrderEvaluation> result = new ArrayList<>();
        result.add(new DemandOrderEvaluation(
                anchor.orderId(),
                anchor.buyerBusinessId(),
                AggregationOrderRole.ANCHOR,
                true,
                anchor.destinationLabel(),
                0,
                anchor.deliveryWindowEnd().getEpochSecond()
                        - anchor.deliveryWindowStart().getEpochSecond(),
                1,
                1,
                List.of(new AggregationConstraintResult(
                        AggregationConstraint.ANCHOR_ORDER,
                        ConstraintOutcome.PASS,
                        null,
                        "This confirmed order is the starting point for the suggestion."))));

        candidates.forEach(candidate -> {
            var assessment = constraints.evaluate(anchor, candidate);
            double candidateScore = scorer.score(
                    candidate.distanceFromAnchorMeters(),
                    assessment.windowOverlapRatio(),
                    assessment.cargoOverlapRatio());
            result.add(new DemandOrderEvaluation(
                    candidate.orderId(),
                    candidate.buyerBusinessId(),
                    AggregationOrderRole.CANDIDATE,
                    assessment.included(),
                    candidate.destinationLabel(),
                    candidate.distanceFromAnchorMeters(),
                    assessment.windowOverlapSeconds(),
                    assessment.cargoOverlapRatio(),
                    candidateScore,
                    assessment.constraintResults()));
        });
        return result.stream()
                .sorted(Comparator.comparing(DemandOrderEvaluation::role)
                        .thenComparing(DemandOrderEvaluation::included, Comparator.reverseOrder())
                        .thenComparing(DemandOrderEvaluation::score, Comparator.reverseOrder())
                        .thenComparing(DemandOrderEvaluation::orderId))
                .toList();
    }

    private AggregationThresholds thresholds() {
        return new AggregationThresholds(
                properties.searchRadiusMeters(),
                properties.maximumDistanceMeters(),
                properties.minimumWindowOverlap(),
                properties.minimumCargoOverlapRatio(),
                properties.candidateLimit());
    }

    private static DemandGroupSuggestion sameAnchor(DemandGroupSuggestion existing, UUID anchorOrderId) {
        if (!existing.anchorOrderId().equals(anchorOrderId)) {
            throw DemandAggregationException.idempotencyConflict();
        }
        return existing;
    }

    private static double groupScore(List<DemandOrderEvaluation> evaluations) {
        return evaluations.stream()
                .filter(value -> value.role() == AggregationOrderRole.CANDIDATE)
                .filter(DemandOrderEvaluation::included)
                .mapToDouble(DemandOrderEvaluation::score)
                .average()
                .orElse(0);
    }

    private static String fingerprint(List<UUID> includedOrderIds, String algorithmVersion) {
        String input = algorithmVersion + ":"
                + includedOrderIds.stream()
                        .map(UUID::toString)
                        .sorted()
                        .reduce((left, right) -> left + "," + right)
                        .orElse("");
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    public record SuggestDemandGroup(UUID requestId, UUID anchorOrderId) {}
}
