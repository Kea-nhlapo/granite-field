package za.co.trademesh.modules.routing.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RouteSegment(
        UUID id,
        int sequence,
        String fromLabel,
        String toLabel,
        List<GeoPoint> geometry,
        long distanceMetres,
        long durationSeconds,
        BigDecimal tollEstimateZar) {

    public RouteSegment {
        geometry = List.copyOf(geometry);
    }
}
