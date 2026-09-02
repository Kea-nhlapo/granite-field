package za.co.trademesh.modules.routing.adapter;

import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.routing.domain.Avoidance;
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.Coordinate;
import za.co.trademesh.modules.routing.domain.RouteCandidateSet;
import za.co.trademesh.modules.routing.domain.RouteRequest;
import za.co.trademesh.modules.routing.domain.RoutingVehicleLimits;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicRouteProviderTest {

    private static final Coordinate JOHANNESBURG = new Coordinate(-26.20, 28.05);
    private static final Coordinate PRETORIA = new Coordinate(-25.75, 28.19);
    private static final RoutingVehicleLimits LIMITS = new RoutingVehicleLimits(4200, 26000);

    private final DeterministicRouteProvider provider = new DeterministicRouteProvider();

    private static RouteRequest request(Set<Avoidance> avoidances) {
        return new RouteRequest(JOHANNESBURG, PRETORIA, List.of(), LIMITS, avoidances);
    }

    /**
     * Determinism must hold across JVM RUNS, not merely within one — #17 develops
     * against this adapter. Hardcoded expectations catch a same-JVM-only
     * determinism (unseeded Random, wall-clock, hash iteration order) that
     * comparing two calls in one process would not.
     */
    @Test
    void producesTheSameCandidatesOnEveryRunForTheSameRequest() {
        List<CandidateRoute> candidates = provider.findCandidates(request(Set.of())).candidates();

        assertThat(candidates).extracting(CandidateRoute::id)
            .containsExactlyElementsOf(DeterministicRouteProviderGoldens.CANDIDATE_IDS);
        assertThat(candidates).extracting(CandidateRoute::distanceMetres)
            .containsExactlyElementsOf(DeterministicRouteProviderGoldens.DISTANCES_METRES);
    }

    /**
     * #17 has every reason to sum segments. Integer division alone left the parts
     * short of the whole by a per-route amount, so the totals disagreed with the
     * route reporting them.
     */
    @Test
    void segmentDistancesAndDurationsSumToTheRouteTotals() {
        RouteRequest viaWaypoints = new RouteRequest(
            JOHANNESBURG,
            PRETORIA,
            List.of(new Coordinate(-25.99, 28.13), new Coordinate(-25.86, 28.19)),
            LIMITS,
            Set.of());

        assertThat(provider.findCandidates(viaWaypoints).candidates()).allSatisfy(candidate -> {
            long summedDistance = candidate.segments().stream()
                .mapToLong(segment -> segment.distanceMetres()).sum();
            java.time.Duration summedDuration = candidate.segments().stream()
                .map(segment -> segment.duration())
                .reduce(java.time.Duration.ZERO, java.time.Duration::plus);

            assertThat(summedDistance).isEqualTo(candidate.distanceMetres());
            assertThat(summedDuration).isEqualTo(candidate.duration());
        });
    }

    @Test
    void producesDifferentCandidatesForDifferentRequests() {
        RouteCandidateSet toPretoria = provider.findCandidates(request(Set.of()));
        RouteCandidateSet elsewhere = provider.findCandidates(new RouteRequest(
            JOHANNESBURG, new Coordinate(-29.86, 31.02), List.of(), LIMITS, Set.of()));

        assertThat(elsewhere.candidates()).extracting(CandidateRoute::id)
            .doesNotContainAnyElementsOf(
                toPretoria.candidates().stream().map(CandidateRoute::id).toList());
    }

    @Test
    void everyCandidateCarriesGeometryDistanceDurationAndProviderVersion() {
        assertThat(provider.findCandidates(request(Set.of())).candidates()).allSatisfy(candidate -> {
            assertThat(candidate.segments()).isNotEmpty();
            assertThat(candidate.segments().getFirst().geometry()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(candidate.distanceMetres()).isPositive();
            assertThat(candidate.duration()).isPositive();
            assertThat(candidate.providerName()).isEqualTo(DeterministicRouteProvider.PROVIDER_NAME);
            assertThat(candidate.providerVersion()).isNotBlank();
            assertThat(candidate.degraded()).isFalse();
        });
    }

    /**
     * Empty means UNKNOWN or not-applicable, never zero. #17's scoring has to be
     * able to tell "this route has no toll" from "we do not know the toll".
     */
    @Test
    void omitsTheTollEstimateEntirelyWhenTollsAreAvoided() {
        assertThat(provider.findCandidates(request(Set.of(Avoidance.TOLLS))).candidates())
            .allSatisfy(candidate -> assertThat(candidate.tollEstimate()).isEmpty());

        assertThat(provider.findCandidates(request(Set.of())).candidates())
            .allSatisfy(candidate -> assertThat(candidate.tollEstimate()).isPresent());
    }
}
