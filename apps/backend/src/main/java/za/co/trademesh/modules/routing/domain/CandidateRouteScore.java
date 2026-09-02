package za.co.trademesh.modules.routing.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CandidateRouteScore(
        UUID candidateId,
        String candidateLabel,
        BigDecimal totalScore,
        BigDecimal confidence,
        Set<RouteOption> options,
        List<RouteFactorScore> factors,
        List<String> reasons) {

    public CandidateRouteScore {
        options = Set.copyOf(options);
        factors = List.copyOf(factors);
        reasons = List.copyOf(reasons);
    }
}
