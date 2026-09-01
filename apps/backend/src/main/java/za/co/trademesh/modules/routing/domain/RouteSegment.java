package za.co.trademesh.modules.routing.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** One leg of a candidate route. Geometry is WGS84, ordered start to end. */
public record RouteSegment(List<Coordinate> geometry, long distanceMetres, Duration duration) {

    public RouteSegment {
        geometry = List.copyOf(geometry);
        if (geometry.size() < 2) {
            throw new IllegalArgumentException("a segment needs at least two points");
        }
        if (distanceMetres <= 0) {
            throw new IllegalArgumentException("distance must be positive, was " + distanceMetres);
        }
        Objects.requireNonNull(duration, "duration is required");
    }
}
