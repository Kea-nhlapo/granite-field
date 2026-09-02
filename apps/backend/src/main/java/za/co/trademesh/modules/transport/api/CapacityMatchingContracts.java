package za.co.trademesh.modules.transport.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.transport.application.CapacityMatchingService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityConstraintOutcome;
import za.co.trademesh.modules.transport.domain.CapacityMatchCandidate;
import za.co.trademesh.modules.transport.domain.CapacityMatchConstraint;
import za.co.trademesh.modules.transport.domain.CapacityMatchSearch;
import za.co.trademesh.modules.transport.domain.CapacityMatchStatus;
import za.co.trademesh.modules.transport.domain.CapacityReservation;
import za.co.trademesh.modules.transport.domain.CapacityReservationStatus;
import za.co.trademesh.modules.transport.domain.CapacityScoreComponent;
import za.co.trademesh.modules.transport.domain.CargoTrait;

public final class CapacityMatchingContracts {

    private CapacityMatchingContracts() {}

    public record SearchRequest(
            @NotNull UUID requestId,
            @NotNull UUID demandGroupSuggestionId,
            @NotNull @Valid CapacityRequest requiredCapacity,
            @NotEmpty List<@NotNull CargoTrait> cargoTraits) {

        CapacityMatchingService.SearchCapacity toCommand() {
            return new CapacityMatchingService.SearchCapacity(
                    requestId,
                    demandGroupSuggestionId,
                    new Capacity(requiredCapacity.weightKg(), requiredCapacity.volumeCubicMetres()),
                    cargoTraits);
        }
    }

    public record CapacityRequest(
            @NotNull @DecimalMin("0.001") @DecimalMax("999999999999.999") @Digits(integer = 12, fraction = 3)
            BigDecimal weightKg,

            @NotNull @DecimalMin("0.001") @DecimalMax("999999999999.999") @Digits(integer = 12, fraction = 3)
            BigDecimal volumeCubicMetres) {}

    public record ReservationRequest(
            @NotNull UUID requestId, @NotNull UUID offerId) {
        CapacityMatchingService.ReserveCapacity toCommand() {
            return new CapacityMatchingService.ReserveCapacity(requestId, offerId);
        }
    }

    public record SearchResponse(
            UUID searchId,
            UUID requestedByBusinessId,
            UUID requestId,
            UUID demandGroupSuggestionId,
            String algorithmVersion,
            CapacityResponse requiredCapacity,
            List<CargoTrait> cargoTraits,
            Instant deliveryWindowStart,
            Instant deliveryWindowEnd,
            int orderCount,
            CapacityMatchStatus status,
            List<CandidateResponse> candidates,
            Instant createdAt) {

        static SearchResponse from(CapacityMatchSearch search) {
            return new SearchResponse(
                    search.id(),
                    search.requestedByBusinessId(),
                    search.clientRequestId(),
                    search.demandGroupSuggestionId(),
                    search.algorithmVersion(),
                    CapacityResponse.from(search.requiredCapacity()),
                    search.cargoTraits(),
                    search.deliveryWindowStart(),
                    search.deliveryWindowEnd(),
                    search.orderCount(),
                    search.status(),
                    search.candidates().stream().map(CandidateResponse::from).toList(),
                    search.createdAt());
        }
    }

    public record CandidateResponse(
            UUID offerId,
            UUID transporterId,
            boolean compatible,
            Integer rank,
            CapacityResponse availableCapacity,
            double addedDistanceMetres,
            long timingOverlapSeconds,
            BigDecimal estimatedCostZar,
            double score,
            List<ConstraintResponse> checks,
            List<ScoreComponentResponse> scoreComponents) {

        static CandidateResponse from(CapacityMatchCandidate candidate) {
            return new CandidateResponse(
                    candidate.offerId(),
                    candidate.transporterId(),
                    candidate.compatible(),
                    candidate.rank(),
                    CapacityResponse.from(candidate.availableCapacity()),
                    candidate.addedDistanceMetres(),
                    candidate.timingOverlapSeconds(),
                    candidate.estimatedCostZar(),
                    candidate.score(),
                    candidate.constraintResults().stream()
                            .map(ConstraintResponse::from)
                            .toList(),
                    candidate.scoreComponents().stream()
                            .map(ScoreComponentResponse::from)
                            .toList());
        }
    }

    public record ConstraintResponse(
            CapacityMatchConstraint constraint, CapacityConstraintOutcome outcome, String explanation) {
        static ConstraintResponse from(za.co.trademesh.modules.transport.domain.CapacityConstraintResult result) {
            return new ConstraintResponse(result.constraint(), result.outcome(), result.explanation());
        }
    }

    public record ScoreComponentResponse(
            String code,
            double rawValue,
            double normalizedValue,
            double weight,
            double contribution,
            String explanation) {
        static ScoreComponentResponse from(CapacityScoreComponent component) {
            return new ScoreComponentResponse(
                    component.code(),
                    component.rawValue(),
                    component.normalizedValue(),
                    component.weight(),
                    component.contribution(),
                    component.explanation());
        }
    }

    public record CapacityResponse(BigDecimal weightKg, BigDecimal volumeCubicMetres) {
        static CapacityResponse from(Capacity capacity) {
            return new CapacityResponse(capacity.weightKg(), capacity.volumeCubicMetres());
        }
    }

    public record ReservationResponse(
            UUID reservationId,
            UUID matchSearchId,
            UUID requestId,
            UUID offerId,
            CapacityResponse reservedCapacity,
            CapacityReservationStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant releasedAt) {

        static ReservationResponse from(CapacityReservation reservation) {
            return new ReservationResponse(
                    reservation.id(),
                    reservation.matchSearchId(),
                    reservation.clientRequestId(),
                    reservation.offerId(),
                    CapacityResponse.from(reservation.reservedCapacity()),
                    reservation.status(),
                    reservation.expiresAt(),
                    reservation.createdAt(),
                    reservation.releasedAt());
        }
    }
}
