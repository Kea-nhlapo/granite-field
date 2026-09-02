package za.co.trademesh.modules.routing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.VehicleLimits;

class ResilientRouteProviderGatewayTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void timesOutThePrimaryAndReturnsAnExplicitFallbackResult() throws Exception {
        RouteProvider slow = request -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RouteProviderException("PRIMARY_INTERRUPTED", "Primary interrupted.", true, interrupted);
            }
            return result("slow");
        };
        RouteProvider fallback = request -> result("fallback");
        var gateway = new ResilientRouteProviderGateway(slow, fallback, executor, Duration.ofMillis(30));

        var resolved = gateway.resolve(request());

        assertThat(resolved.fallbackUsed()).isTrue();
        assertThat(resolved.fallbackReason()).isEqualTo("ROUTE_PROVIDER_TIMEOUT");
        assertThat(resolved.providerResult().providerName()).isEqualTo("fallback");
    }

    @Test
    void returnsASafeFailureWhenBothProvidersFail() {
        RouteProvider unavailable = request -> {
            throw new RouteProviderException("DEMO_DOWN", "Provider unavailable.", true);
        };
        var gateway = new ResilientRouteProviderGateway(unavailable, unavailable, executor, Duration.ofMillis(100));

        assertThatThrownBy(() -> gateway.resolve(request()))
                .isInstanceOf(RouteProviderException.class)
                .extracting(failure -> ((RouteProviderException) failure).code())
                .isEqualTo("ROUTE_PROVIDER_AND_FALLBACK_UNAVAILABLE");
    }

    private static RouteProvider.ProviderRequest request() {
        return new RouteProvider.ProviderRequest(
                new GeoPoint("Johannesburg", -26.2041, 28.0473),
                new GeoPoint("Pretoria", -25.7479, 28.2293),
                List.of(),
                new VehicleLimits(
                        new BigDecimal("5000.000"),
                        new BigDecimal("4.200"),
                        new BigDecimal("2.500"),
                        new BigDecimal("12.000")),
                List.of(),
                3);
    }

    private static RouteProvider.ProviderResult result(String providerName) {
        var origin = new GeoPoint(null, -26.2041, 28.0473);
        var destination = new GeoPoint(null, -25.7479, 28.2293);
        var segment = new RouteProvider.ProviderSegment(
                0, "Johannesburg", "Pretoria", List.of(origin, destination), 60_000, 3_600, new BigDecimal("0.00"));
        return new RouteProvider.ProviderResult(
                providerName,
                "test/v1",
                List.of(new RouteProvider.ProviderCandidate(
                        "candidate-1",
                        "TEST",
                        List.of(origin, destination),
                        60_000,
                        3_600,
                        new BigDecimal("0.00"),
                        List.of(segment))));
    }
}
