package za.co.trademesh.modules.routing.infrastructure;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.routing.application.ResilientRouteProviderGateway;
import za.co.trademesh.modules.routing.application.RouteProvider;
import za.co.trademesh.modules.routing.application.RouteProviderGateway;
import za.co.trademesh.modules.routing.application.RoutingProviderProperties;

@Configuration
class RoutingProviderConfiguration {

    @Bean("routingPrimaryProvider")
    RouteProvider primaryProvider(RoutingProviderProperties properties) {
        String provider = properties.provider() == null
                ? ""
                : properties.provider().strip().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "mock" -> new DeterministicMockRouteProvider();
            case "google" -> new GoogleDirectionsRouteProvider(RestClient.builder(), properties);
            default -> throw new IllegalStateException("Unsupported routing provider: " + provider);
        };
    }

    @Bean("routingFallbackProvider")
    RouteProvider fallbackProvider() {
        return new StraightLineFallbackRouteProvider();
    }

    @Bean(name = "routingProviderExecutor", destroyMethod = "shutdownNow")
    ExecutorService routingProviderExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "routing-provider");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    RouteProviderGateway routeProviderGateway(
            @Qualifier("routingPrimaryProvider") RouteProvider primary,
            @Qualifier("routingFallbackProvider") RouteProvider fallback,
            @Qualifier("routingProviderExecutor") ExecutorService executor,
            RoutingProviderProperties properties) {
        return new ResilientRouteProviderGateway(primary, fallback, executor, properties.timeout());
    }
}
