package za.co.trademesh.modules.routing.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** One leg of a candidate route. Geometry is WGS84, ordered start to end. */
public record RouteSegment(List<Coordinate> geometry, long distanceMetres, Duration duration) {

    public RouteSegment {
        Objects.requireNonNull(geometry, "geometry is required");
        geometry = List.copyOf(geometry);
        if (geometry.size() < 2) {
            throw new IllegalArgumentException("a segment needs at least two points");
        }
        if (distanceMetres <= 0) {
            throw new IllegalArgumentException("distance must be positive, was " + distanceMetres);
        }
        Objects.requireNonNull(duration, "duration is required");
        // Sign matters as much as nullity: a negative duration reaching #17 makes
        // a broken candidate sort as the fastest one.
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive, was " + duration);
        }
    }

    public Coordinate start() {
        return geometry.getFirst();
    }

    public Coordinate end() {
        return geometry.getLast();
    }
}
