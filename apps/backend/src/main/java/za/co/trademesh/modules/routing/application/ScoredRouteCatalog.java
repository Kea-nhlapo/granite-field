package za.co.trademesh.modules.routing.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Scored route choices exposed to shipment without exposing routing persistence. */
public interface ScoredRouteCatalog {

    Optional<ScoredRoute> findScoredRoute(UUID requestedByBusinessId, UUID assessmentId, UUID candidateId);

    record ScoredRoute(
            UUID assessmentId,
            UUID calculationId,
            UUID candidateId,
            String cargoProfile,
            String algorithmVersion,
            BigDecimal totalScore,
            BigDecimal confidence,
            List<RoutePoint> geometry,
            long distanceMetres,
            long durationSeconds,
            BigDecimal tollEstimateZar) {
        public ScoredRoute {
            geometry = List.copyOf(geometry);
        }
    }

    record RoutePoint(double latitude, double longitude) {}
}
