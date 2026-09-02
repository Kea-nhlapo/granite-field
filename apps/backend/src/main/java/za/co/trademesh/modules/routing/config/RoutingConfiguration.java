package za.co.trademesh.modules.routing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import za.co.trademesh.modules.routing.adapter.DeterministicRouteProvider;
import za.co.trademesh.modules.routing.adapter.FallbackRouteProvider;
import za.co.trademesh.modules.routing.adapter.TimeLimitedRouteProvider;
import za.co.trademesh.modules.routing.port.RouteProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Assembles the route provider callers actually receive.
 *
 * <p>Without this, the decorators were dead code: the only obtainable provider
 * was a bare DeterministicRouteProvider with no timeout and no fallback, so the
 * ticket's "provider failures have timeouts and clear fallback behavior"
 * criterion was met by classes nothing constructed.
 *
 * <p>ORDER MATTERS. Fallback is outermost so it also covers a timeout, which is
 * itself a provider failure:
 *
 * <pre>Fallback( TimeLimited( Deterministic ) )</pre>
 *
 * Inverting these would let a timeout escape past the fallback to the caller.
 */
@Configuration
public class RoutingConfiguration {

    /**
     * Owned here, and shut down with the context. Threads are daemons so a
     * delegate abandoned by a timeout can never hold up JVM shutdown.
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService routeProviderExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "route-provider");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public RouteProvider routeProvider(
        RoutingProperties properties, ExecutorService routeProviderExecutor) {

        return new FallbackRouteProvider(
            new TimeLimitedRouteProvider(
                new DeterministicRouteProvider(), properties.providerTimeout(), routeProviderExecutor));
    }
}
