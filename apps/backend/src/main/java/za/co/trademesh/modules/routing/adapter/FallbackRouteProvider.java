package za.co.trademesh.modules.routing.adapter;

import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.Coordinate;
import za.co.trademesh.modules.routing.domain.RouteCandidateSet;
import za.co.trademesh.modules.routing.domain.RouteProviderException;
import za.co.trademesh.modules.routing.domain.RouteRequest;
import za.co.trademesh.modules.routing.domain.RouteSegment;
import za.co.trademesh.modules.routing.port.RouteProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Degrades instead of failing when the routing engine is unavailable.
 *
 * <p>The alternative — propagating the failure — would take the whole shipment
 * flow down whenever a map vendor has a bad afternoon. The alternative in the
 * other direction, returning a guess that looks like a real route, is worse:
 * downstream scoring in issue #17 would rank a straight line against surveyed
 * roads with no way to tell them apart.
 *
 * <p>So the estimate is returned AND labelled: degraded = true on the candidate
 * itself, straight-line geometry, great-circle distance, and NO toll estimate,
 * because a straight line cannot know about tolls. Empty means unknown here,
 * which is exactly what it is.
 */
public class FallbackRouteProvider implements RouteProvider {

    public static final String PROVIDER_NAME = "great-circle-fallback";
    public static final String PROVIDER_VERSION = "1.0.0";

    /** Conservative: a straight line ignores terrain, so do not promise highway speed. */
    private static final double ESTIMATED_SPEED_METRES_PER_SECOND = 16.7;

    private final RouteProvider delegate;

    public FallbackRouteProvider(RouteProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
    }

    @Override
    public RouteCandidateSet findCandidates(RouteRequest request) {
        try {
            return delegate.findCandidates(request);
        } catch (RouteProviderException e) {
            return RouteCandidateSet.of(request, List.of(straightLineEstimate(request)));
        }
    }

    private CandidateRoute straightLineEstimate(RouteRequest request) {
        Coordinate origin = request.origin();
        Coordinate destination = request.destination();
        long distanceMetres = Math.max(1, GreatCircle.metresBetween(origin, destination));
        Duration duration = Duration.ofSeconds(
            Math.max(1, Math.round(distanceMetres / ESTIMATED_SPEED_METRES_PER_SECOND)));

        return new CandidateRoute(
            UUID.nameUUIDFromBytes(
                (PROVIDER_NAME + origin + destination).getBytes(StandardCharsets.UTF_8)).toString(),
            List.of(new RouteSegment(List.of(origin, destination), distanceMetres, duration)),
            distanceMetres,
            duration,
            Optional.empty(),
            PROVIDER_NAME,
            PROVIDER_VERSION,
            true);
    }
}
