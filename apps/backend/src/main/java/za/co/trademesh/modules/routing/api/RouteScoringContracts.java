package za.co.trademesh.modules.routing.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import za.co.trademesh.modules.routing.application.RouteScoringService;
import za.co.trademesh.modules.routing.domain.CandidateRouteScore;
import za.co.trademesh.modules.routing.domain.RouteAssessment;
import za.co.trademesh.modules.routing.domain.RouteFactor;
import za.co.trademesh.modules.routing.domain.RouteFactorScore;
import za.co.trademesh.modules.routing.domain.RouteOption;

public final class RouteScoringContracts {

    private RouteScoringContracts() {}

    public record ScoreRoutesRequest(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 64) String cargoProfile,
            Map<@NotNull RouteFactor, @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 6) BigDecimal>
                    weightOverrides) {

        RouteScoringService.ScoreRoutes toCommand() {
            return new RouteScoringService.ScoreRoutes(requestId, cargoProfile, weightOverrides);
        }
    }

    public record RouteAssessmentResponse(
            UUID assessmentId,
            UUID calculationId,
            UUID requestedByBusinessId,
            UUID requestId,
            String cargoProfile,
            String algorithmVersion,
            String scoreScale,
            Map<RouteFactor, BigDecimal> weights,
            UUID recommendedCandidateId,
            Map<RouteOption, UUID> options,
            List<CandidateScoreResponse> candidates,
            Instant createdAt) {

        static RouteAssessmentResponse from(RouteAssessment assessment) {
            EnumMap<RouteOption, UUID> options = new EnumMap<>(RouteOption.class);
            assessment.candidates().forEach(candidate -> candidate
                    .options()
                    .forEach(option -> options.put(option, candidate.candidateId())));
            return new RouteAssessmentResponse(
                    assessment.id(),
                    assessment.calculationId(),
                    assessment.requestedByBusinessId(),
                    assessment.clientRequestId(),
                    assessment.cargoProfile(),
                    assessment.algorithmVersion(),
                    "0 is best; 1 is worst",
                    assessment.weights(),
                    assessment.recommendedCandidateId(),
                    options,
                    assessment.candidates().stream()
                            .map(CandidateScoreResponse::from)
                            .toList(),
                    assessment.createdAt());
        }
    }

    public record CandidateScoreResponse(
            UUID candidateId,
            String label,
            BigDecimal totalScore,
            BigDecimal confidence,
            List<RouteOption> options,
            List<FactorScoreResponse> factors,
            List<String> reasons) {

        static CandidateScoreResponse from(CandidateRouteScore score) {
            return new CandidateScoreResponse(
                    score.candidateId(),
                    score.candidateLabel(),
                    score.totalScore(),
                    score.confidence(),
                    score.options().stream().sorted().toList(),
                    score.factors().stream().map(FactorScoreResponse::from).toList(),
                    score.reasons());
        }
    }

    public record FactorScoreResponse(
            RouteFactor factor,
            BigDecimal rawValue,
            String rawUnit,
            BigDecimal normalizedValue,
            BigDecimal weight,
            BigDecimal contribution,
            boolean dataAvailable) {

        static FactorScoreResponse from(RouteFactorScore score) {
            return new FactorScoreResponse(
                    score.factor(),
                    score.rawValue(),
                    score.rawUnit(),
                    score.normalizedValue(),
                    score.weight(),
                    score.contribution(),
                    score.dataAvailable());
        }
    }
}
