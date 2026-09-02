package za.co.trademesh.modules.routing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.routing.application.RouteProvider;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAvoidance;
import za.co.trademesh.modules.routing.domain.VehicleLimits;

class DeterministicMockRouteProviderTest {

    private final DeterministicMockRouteProvider provider = new DeterministicMockRouteProvider();

    @Test
    void returnsSeveralDeterministicNormalizedCandidates() throws Exception {
        var request = request(List.of());

        var first = provider.calculate(request);
        var second = provider.calculate(request);

        assertThat(first).isEqualTo(second);
        assertThat(first.providerVersion()).isEqualTo("mock-route/v1");
        assertThat(first.candidates()).hasSize(3);
        assertThat(first.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.geometry().getFirst()).isEqualTo(request.origin());
            assertThat(candidate.geometry().getLast()).isEqualTo(request.destination());
            assertThat(candidate.distanceMetres())
                    .isEqualTo(candidate.segments().stream()
                            .mapToLong(RouteProvider.ProviderSegment::distanceMetres)
                            .sum());
            assertThat(candidate.durationSeconds())
                    .isEqualTo(candidate.segments().stream()
                            .mapToLong(RouteProvider.ProviderSegment::durationSeconds)
                            .sum());
        });
    }

    @Test
    void appliesTollAvoidanceWithoutChangingTheProviderContract() throws Exception {
        var result = provider.calculate(request(List.of(RouteAvoidance.TOLLS)));

        assertThat(result.candidates())
                .allSatisfy(candidate -> assertThat(candidate.tollEstimateZar()).isZero());
    }

    private static RouteProvider.ProviderRequest request(List<RouteAvoidance> avoidances) {
        return new RouteProvider.ProviderRequest(
                new GeoPoint("Johannesburg", -26.2041, 28.0473),
                new GeoPoint("Pretoria", -25.7479, 28.2293),
                List.of(new GeoPoint("Midrand", -25.9992, 28.1263)),
                new VehicleLimits(
                        new BigDecimal("5000.000"),
                        new BigDecimal("4.200"),
                        new BigDecimal("2.500"),
                        new BigDecimal("12.000")),
                avoidances,
                3);
    }
}
