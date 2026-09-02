package za.co.trademesh.modules.routing.domain;

import java.util.ArrayList;
import java.util.List;
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
        require(origin != null, "origin is required");
        require(destination != null, "destination is required");
        require(vehicleLimits != null, "vehicleLimits is required");
        require(waypoints != null, "waypoints is required (use an empty list)");
        require(avoidances != null, "avoidances is required (use an empty set)");
        waypoints = List.copyOf(waypoints);
        avoidances = Set.copyOf(avoidances);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Origin, then each waypoint in order, then destination. */
    public List<Coordinate> orderedStops() {
        List<Coordinate> stops = new ArrayList<>(waypoints.size() + 2);
        stops.add(origin);
        stops.addAll(waypoints);
        stops.add(destination);
        return List.copyOf(stops);
    }
}
