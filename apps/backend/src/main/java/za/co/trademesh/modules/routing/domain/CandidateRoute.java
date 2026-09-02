package za.co.trademesh.modules.routing.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CandidateRoute(
        UUID id,
        int sequence,
        String providerCandidateKey,
        String label,
        List<GeoPoint> geometry,
        long distanceMetres,
        long durationSeconds,
        BigDecimal tollEstimateZar,
        List<RouteSegment> segments) {

    public CandidateRoute {
        geometry = List.copyOf(geometry);
        segments = List.copyOf(segments);
    }
}
