package za.co.trademesh.modules.routing.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The result of ONE calculation, with its own identity.
 *
 * <p>This is how "routes can be recalculated without overwriting the route
 * originally approved for a shipment" is satisfied: recalculating never mutates
 * an existing set, it produces a new one with a new id. Whoever approves a route
 * holds a reference to a specific set, and that set is immutable.
 *
 * <p>The id identifies the CALCULATION, so it is fresh every time even when a
 * deterministic provider returns byte-identical candidates. Determinism is a
 * property of the candidates, not of the calculation's identity.
 */
public record RouteCandidateSet(String id, RouteRequest request, List<CandidateRoute> candidates) {

    public RouteCandidateSet {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(candidates, "candidates is required");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("a candidate set needs at least one candidate route");
        }
        // The whole point of this type is that a shipment can hold a traceable
        // reference to an approved route. A candidate for a different journey
        // would make that reference a lie, so the link is checked here rather
        // than trusted.
        for (CandidateRoute candidate : candidates) {
            if (!candidate.start().equals(request.origin())) {
                throw new IllegalArgumentException(
                    "candidate " + candidate.id() + " does not start at the requested origin");
            }
            if (!candidate.end().equals(request.destination())) {
                throw new IllegalArgumentException(
                    "candidate " + candidate.id() + " does not end at the requested destination");
            }
        }
    }

    public static RouteCandidateSet of(RouteRequest request, List<CandidateRoute> candidates) {
        return new RouteCandidateSet(UUID.randomUUID().toString(), request, candidates);
    }
}
