package za.co.trademesh.modules.routing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RouteAssessment(
        UUID id,
        UUID requestedByBusinessId,
        UUID calculationId,
        UUID clientRequestId,
        String inputFingerprint,
        String cargoProfile,
        String algorithmVersion,
        Map<RouteFactor, BigDecimal> weights,
        UUID recommendedCandidateId,
        List<CandidateRouteScore> candidates,
        UUID createdByUserId,
        Instant createdAt) {

    public RouteAssessment {
        weights = Map.copyOf(weights);
        candidates = List.copyOf(candidates);
    }
}
