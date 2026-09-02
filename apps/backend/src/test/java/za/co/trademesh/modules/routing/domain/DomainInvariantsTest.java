package za.co.trademesh.modules.routing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants the records must enforce themselves, whatever a provider hands them. */
class DomainInvariantsTest {

    private static final Coordinate ORIGIN = new Coordinate(-26.20, 28.05);
    private static final Coordinate DESTINATION = new Coordinate(-25.75, 28.19);

    private static RouteSegment segment(Duration duration) {
        return new RouteSegment(List.of(ORIGIN, DESTINATION), 51_000, duration);
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThatThrownBy(() -> new Coordinate(Double.NaN, 28.05))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("finite");

        assertThatThrownBy(() -> new Coordinate(-26.20, Double.POSITIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("finite");
    }

    /**
     * A negative duration would make a broken candidate sort as the fastest one
     * in #17's scoring, so the record refuses it rather than trusting providers.
     */
    @Test
    void rejectsNonPositiveDurations() {
        assertThatThrownBy(() -> segment(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duration must be positive");

        assertThatThrownBy(() -> segment(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duration must be positive");

        assertThatThrownBy(() -> new CandidateRoute(
            "id", List.of(segment(Duration.ofMinutes(45))), 51_000, Duration.ZERO,
            Optional.empty(), "p", "1.0.0", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duration must be positive");
    }

    /**
     * BigDecimal.equals is scale-sensitive and records derive equals from their
     * components, so without normalisation these two rands would be unequal.
     */
    @Test
    void treatsTheSameAmountAtDifferentScalesAsEqual() {
        assertThat(Money.of(new BigDecimal("1.50"), "ZAR"))
            .isEqualTo(Money.of(new BigDecimal("1.5"), "ZAR"));

        assertThat(Money.of(new BigDecimal("1.5"), "ZAR").amount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsCandidatesThatDoNotMatchTheirRequest() {
        RouteRequest request = new RouteRequest(
            ORIGIN, DESTINATION, List.of(), new RoutingVehicleLimits(4200, 26000), Set.of());

        Coordinate elsewhere = new Coordinate(-29.86, 31.02);
        CandidateRoute wrongJourney = new CandidateRoute(
            "wrong",
            List.of(new RouteSegment(List.of(elsewhere, DESTINATION), 51_000, Duration.ofMinutes(45))),
            51_000, Duration.ofMinutes(45), Optional.empty(), "p", "1.0.0", false);

        assertThatThrownBy(() -> RouteCandidateSet.of(request, List.of(wrongJourney)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not start at the requested origin");
    }

    @Test
    void namesTheMissingFieldWhenACollectionIsNull() {
        assertThatThrownBy(() -> new RouteRequest(
            ORIGIN, DESTINATION, null, new RoutingVehicleLimits(4200, 26000), Set.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("waypoints");

        assertThatThrownBy(() -> new RouteRequest(
            ORIGIN, DESTINATION, List.of(), new RoutingVehicleLimits(4200, 26000), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("avoidances");
    }
}
