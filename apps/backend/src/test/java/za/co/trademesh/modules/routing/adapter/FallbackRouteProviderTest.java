package za.co.trademesh.modules.routing.adapter;

import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.Coordinate;
import za.co.trademesh.modules.routing.domain.RouteCandidateSet;
import za.co.trademesh.modules.routing.domain.RouteProviderException;
import za.co.trademesh.modules.routing.domain.RouteRequest;
import za.co.trademesh.modules.routing.domain.RoutingVehicleLimits;
import za.co.trademesh.modules.routing.port.RouteProvider;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackRouteProviderTest {

    private static final RouteRequest JOHANNESBURG_TO_PRETORIA = new RouteRequest(
        new Coordinate(-26.20, 28.05),
        new Coordinate(-25.75, 28.19),
        List.of(),
        new RoutingVehicleLimits(4200, 26000),
        Set.of());

    @Test
    void returnsADegradedStraightLineEstimateWhenTheDelegateFails() {
        RouteProvider failing = request -> {
            throw new RouteProviderException("upstream unavailable");
        };

        RouteCandidateSet set =
            new FallbackRouteProvider(failing).findCandidates(JOHANNESBURG_TO_PRETORIA);

        assertThat(set.candidates()).hasSize(1);
        CandidateRoute fallback = set.candidates().getFirst();

        assertThat(fallback.degraded())
            .as("a scorer must be able to tell a guess from a real route")
            .isTrue();
        assertThat(fallback.tollEstimate())
            .as("a straight line cannot know about tolls")
            .isEmpty();
        assertThat(fallback.segments().getFirst().geometry())
            .containsExactly(
                JOHANNESBURG_TO_PRETORIA.origin(), JOHANNESBURG_TO_PRETORIA.destination());
        assertThat(fallback.distanceMetres())
            .as("great-circle Johannesburg to Pretoria is roughly 51 km")
            .isBetween(45_000L, 60_000L);
        assertThat(fallback.duration()).isPositive();
    }

    /**
     * Composition order must not decide whether the fallback works. An unwrapped
     * RuntimeException from any delegate has to degrade, not escape.
     */
    @Test
    void degradesEvenWhenTheDelegateThrowsSomethingOtherThanRouteProviderException() {
        RouteProvider broken = request -> {
            throw new IllegalStateException("delegate blew up without translating");
        };

        RouteCandidateSet set =
            new FallbackRouteProvider(broken).findCandidates(JOHANNESBURG_TO_PRETORIA);

        assertThat(set.candidates()).singleElement()
            .satisfies(candidate -> assertThat(candidate.degraded()).isTrue());
    }

    @Test
    void passesThroughAndMarksNothingDegradedWhenTheDelegateSucceeds() {
        RouteProvider working = new DeterministicRouteProvider();

        RouteCandidateSet set =
            new FallbackRouteProvider(working).findCandidates(JOHANNESBURG_TO_PRETORIA);

        assertThat(set.candidates())
            .allSatisfy(candidate -> assertThat(candidate.degraded()).isFalse());
    }
}
