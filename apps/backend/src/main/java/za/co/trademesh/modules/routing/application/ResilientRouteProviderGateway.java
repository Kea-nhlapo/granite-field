package za.co.trademesh.modules.routing.application;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ResilientRouteProviderGateway implements RouteProviderGateway {

    private final RouteProvider primary;
    private final RouteProvider fallback;
    private final ExecutorService executor;
    private final Duration timeout;

    public ResilientRouteProviderGateway(
            RouteProvider primary, RouteProvider fallback, ExecutorService executor, Duration timeout) {
        this.primary = Objects.requireNonNull(primary);
        this.fallback = Objects.requireNonNull(fallback);
        this.executor = Objects.requireNonNull(executor);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Route provider timeout must be positive");
        }
        this.timeout = timeout;
    }

    @Override
    public ResolvedRoutes resolve(RouteProvider.ProviderRequest request) throws RouteProviderException {
        try {
            return new ResolvedRoutes(invoke(primary, request), false, null);
        } catch (RouteProviderException primaryFailure) {
            try {
                return new ResolvedRoutes(invoke(fallback, request), true, primaryFailure.code());
            } catch (RouteProviderException fallbackFailure) {
                throw new RouteProviderException(
                        "ROUTE_PROVIDER_AND_FALLBACK_UNAVAILABLE",
                        "No route provider is currently available.",
                        primaryFailure.retryable() || fallbackFailure.retryable(),
                        fallbackFailure);
            }
        }
    }

    private RouteProvider.ProviderResult invoke(RouteProvider provider, RouteProvider.ProviderRequest request)
            throws RouteProviderException {
        Future<RouteProvider.ProviderResult> future = executor.submit(() -> provider.calculate(request));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutFailure) {
            future.cancel(true);
            throw new RouteProviderException(
                    "ROUTE_PROVIDER_TIMEOUT", "The route provider did not respond in time.", true, timeoutFailure);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RouteProviderException(
                    "ROUTE_PROVIDER_INTERRUPTED", "Route calculation was interrupted.", true, interrupted);
        } catch (ExecutionException executionFailure) {
            Throwable cause = executionFailure.getCause();
            if (cause instanceof RouteProviderException providerFailure) {
                throw providerFailure;
            }
            throw new RouteProviderException(
                    "ROUTE_PROVIDER_UNEXPECTED_ERROR", "The route provider returned an unexpected error.", true, cause);
        }
    }
}
