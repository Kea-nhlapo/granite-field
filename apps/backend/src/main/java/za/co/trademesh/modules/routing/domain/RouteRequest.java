package za.co.trademesh.modules.routing.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What the domain asks for. Collections are defensively copied: a record is only
 * shallowly immutable, so without the copy the caller keeps a live reference to
 * the list this request is holding.
 */
public record RouteRequest(
    Coordinate origin,
    Coordinate destination,
    List<Coordinate> waypoints,
    RoutingVehicleLimits vehicleLimits,
    Set<Avoidance> avoidances) {

    public RouteRequest {
        if (origin == null) {
            throw new IllegalArgumentException("origin is required");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is required");
        }
        Objects.requireNonNull(vehicleLimits, "vehicleLimits is required");
        waypoints = List.copyOf(waypoints);
        // LinkedHashSet keeps a stable order, which the deterministic adapter
        // relies on when it builds its canonical form of this request.
        avoidances = Set.copyOf(new LinkedHashSet<>(avoidances));
    }

    /** Origin, then each waypoint in order, then destination. */
    public List<Coordinate> orderedStops() {
        return java.util.stream.Stream
            .concat(java.util.stream.Stream.concat(
                java.util.stream.Stream.of(origin), waypoints.stream()),
                java.util.stream.Stream.of(destination))
            .toList();
    }
}
