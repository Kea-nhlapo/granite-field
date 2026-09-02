package za.co.trademesh.modules.routing.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.routing")
public record RoutingProviderProperties(
        String provider,
        String directionsEndpoint,
        String mapsApiKey,
        Duration timeout,
        int maxWaypoints,
        int maximumCandidates) {}
