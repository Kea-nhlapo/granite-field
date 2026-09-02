package za.co.trademesh.modules.routing.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import za.co.trademesh.modules.routing.adapter.FallbackRouteProvider;
import za.co.trademesh.modules.routing.domain.Coordinate;
import za.co.trademesh.modules.routing.domain.RouteRequest;
import za.co.trademesh.modules.routing.domain.RoutingVehicleLimits;
import za.co.trademesh.modules.routing.port.RouteProvider;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decorators were previously constructed nowhere, so the timeout-and-
 * fallback acceptance criterion was satisfied only by classes no caller could
 * reach. This asserts a caller actually gets the composed provider.
 *
 * <p>No Spring Boot application context and no database: the routing module has
 * no reason to need either.
 */
class RoutingConfigurationTest {

    private static final RouteRequest REQUEST = new RouteRequest(
        new Coordinate(-26.20, 28.05),
        new Coordinate(-25.75, 28.19),
        List.of(),
        new RoutingVehicleLimits(4200, 26000),
        Set.of());

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of())
        .withBean(RoutingProperties.class, () -> new RoutingProperties(Duration.ofSeconds(5)))
        .withUserConfiguration(RoutingConfiguration.class);

    @Test
    void exposesARouteProviderWithFallbackOutermost() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RouteProvider.class);
            assertThat(context.getBean(RouteProvider.class))
                .as("fallback must wrap the timeout, so a timeout also degrades")
                .isInstanceOf(FallbackRouteProvider.class);
        });
    }

    @Test
    void theWiredProviderProducesUsableCandidates() {
        runner.run(context -> assertThat(
            context.getBean(RouteProvider.class).findCandidates(REQUEST).candidates())
            .isNotEmpty()
            .allSatisfy(candidate -> assertThat(candidate.degraded()).isFalse()));
    }

    @Test
    void shutsTheExecutorDownWithTheContext() {
        runner.run(context -> {
            java.util.concurrent.ExecutorService executor =
                context.getBean(java.util.concurrent.ExecutorService.class);
            assertThat(executor.isShutdown()).isFalse();
            ((org.springframework.context.ConfigurableApplicationContext) context.getSourceApplicationContext())
                .close();
            assertThat(executor.isShutdown()).isTrue();
        });
    }
}
