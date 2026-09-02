package za.co.trademesh.modules.routing.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One route option. Carries the provider name and version so a stored decision
 * can be traced to the engine that produced it.
 *
 * <p>{@code tollEstimate} empty means UNKNOWN or not-applicable — never zero.
 * Issue #17's scoring depends on telling those apart.
 *
 * <p>{@code degraded} marks an estimate produced by fallback rather than a real
 * routing engine. It is on the candidate, not the set, so a scorer cannot read
 * a candidate without seeing it.
 */
public record CandidateRoute(
    String id,
    List<RouteSegment> segments,
    long distanceMetres,
    Duration duration,
    Optional<Money> tollEstimate,
    String providerName,
    String providerVersion,
    boolean degraded) {

    public CandidateRoute {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(segments, "segments is required");
        segments = List.copyOf(segments);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("a candidate route needs at least one segment");
        }
        if (distanceMetres <= 0) {
            throw new IllegalArgumentException("distance must be positive, was " + distanceMetres);
        }
        Objects.requireNonNull(duration, "duration is required");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive, was " + duration);
        }
        Objects.requireNonNull(tollEstimate, "tollEstimate is required (use Optional.empty())");
        Objects.requireNonNull(providerName, "providerName is required");
        Objects.requireNonNull(providerVersion, "providerVersion is required");
    }

    public Coordinate start() {
        return segments.getFirst().start();
    }

    public Coordinate end() {
        return segments.getLast().end();
    }
}
