package za.co.trademesh.modules.routing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteCalculation(
        UUID id,
        UUID requestedByBusinessId,
        UUID clientRequestId,
        UUID recalculationOfId,
        String inputFingerprint,
        GeoPoint origin,
        GeoPoint destination,
        List<GeoPoint> waypoints,
        VehicleLimits vehicleLimits,
        List<RouteAvoidance> avoidances,
        String providerName,
        String providerVersion,
        boolean fallbackUsed,
        String fallbackReason,
        List<CandidateRoute> candidates,
        UUID createdByUserId,
        Instant createdAt) {

    public RouteCalculation {
        waypoints = List.copyOf(waypoints);
        avoidances = List.copyOf(avoidances);
        candidates = List.copyOf(candidates);
    }
}
