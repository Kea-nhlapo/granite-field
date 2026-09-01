package za.co.trademesh.modules.routing.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteRequestTest {

    private static final Coordinate JOHANNESBURG = new Coordinate(-26.20, 28.05);
    private static final Coordinate PRETORIA = new Coordinate(-25.75, 28.19);
    private static final RoutingVehicleLimits LIMITS = new RoutingVehicleLimits(4200, 26000);

    @Test
    void rejectsAMissingOriginOrDestination() {
        assertThatThrownBy(() -> new RouteRequest(null, PRETORIA, List.of(), LIMITS, Set.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("origin");

        assertThatThrownBy(() -> new RouteRequest(JOHANNESBURG, null, List.of(), LIMITS, Set.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("destination");
    }

    @Test
    void rejectsCoordinatesOutsideTheValidRange() {
        assertThatThrownBy(() -> new Coordinate(-91.0, 28.05))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("latitude");

        assertThatThrownBy(() -> new Coordinate(-26.20, 181.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("longitude");
    }

    @Test
    void rejectsNonPositiveVehicleLimits() {
        assertThatThrownBy(() -> new RoutingVehicleLimits(0, 26000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("height");

        assertThatThrownBy(() -> new RoutingVehicleLimits(4200, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("weight");
    }

    @Test
    void preservesWaypointOrder() {
        Coordinate midrand = new Coordinate(-25.99, 28.13);
        Coordinate centurion = new Coordinate(-25.86, 28.19);

        RouteRequest request =
            new RouteRequest(JOHANNESBURG, PRETORIA, List.of(midrand, centurion), LIMITS, Set.of());

        assertThat(request.waypoints()).containsExactly(midrand, centurion);
    }

    /**
     * Records are only SHALLOWLY immutable. Without a defensive copy the caller
     * keeps a live reference to the list the request is holding.
     */
    @Test
    void isUnaffectedByLaterMutationOfTheCallersWaypointList() {
        List<Coordinate> waypoints = new ArrayList<>(List.of(new Coordinate(-25.99, 28.13)));
        RouteRequest request = new RouteRequest(JOHANNESBURG, PRETORIA, waypoints, LIMITS, Set.of());

        waypoints.clear();

        assertThat(request.waypoints()).hasSize(1);
    }

    @Test
    void isUnaffectedByLaterMutationOfTheCallersAvoidanceSet() {
        Set<Avoidance> avoidances = new java.util.HashSet<>(Set.of(Avoidance.TOLLS));
        RouteRequest request =
            new RouteRequest(JOHANNESBURG, PRETORIA, List.of(), LIMITS, avoidances);

        avoidances.clear();

        assertThat(request.avoidances()).containsExactly(Avoidance.TOLLS);
    }
}
