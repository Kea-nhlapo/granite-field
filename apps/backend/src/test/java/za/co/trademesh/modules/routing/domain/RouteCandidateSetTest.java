package za.co.trademesh.modules.routing.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RouteCandidateSetTest {

    private static final RouteRequest REQUEST = new RouteRequest(
        new Coordinate(-26.20, 28.05),
        new Coordinate(-25.75, 28.19),
        List.of(),
        new RoutingVehicleLimits(4200, 26000),
        Set.of());

    private static CandidateRoute candidate(String id) {
        return new CandidateRoute(
            id,
            List.of(new RouteSegment(
                List.of(new Coordinate(-26.20, 28.05), new Coordinate(-25.75, 28.19)),
                51_000,
                Duration.ofMinutes(45))),
            51_000,
            Duration.ofMinutes(45),
            Optional.empty(),
            "test-provider",
            "1.0.0",
            false);
    }

    /**
     * The acceptance criterion is that a shipment's APPROVED route survives a
     * recalculation. Modelled here as: a recalculation yields a new set with its
     * own identity, and the earlier set is untouched.
     */
    @Test
    void recalculationProducesANewSetAndLeavesTheOriginalUntouched() {
        RouteCandidateSet approved = RouteCandidateSet.of(REQUEST, List.of(candidate("a")));

        RouteCandidateSet recalculated = RouteCandidateSet.of(REQUEST, List.of(candidate("b")));

        assertThat(recalculated.id()).isNotEqualTo(approved.id());
        assertThat(approved.candidates()).extracting(CandidateRoute::id).containsExactly("a");
        assertThat(recalculated.candidates()).extracting(CandidateRoute::id).containsExactly("b");
    }

    @Test
    void isUnaffectedByLaterMutationOfTheCallersCandidateList() {
        List<CandidateRoute> candidates = new ArrayList<>(List.of(candidate("a")));
        RouteCandidateSet set = RouteCandidateSet.of(REQUEST, candidates);

        candidates.clear();

        assertThat(set.candidates()).hasSize(1);
    }

    @Test
    void rejectsAnEmptyCandidateList() {
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> RouteCandidateSet.of(REQUEST, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("candidate");
    }
}
