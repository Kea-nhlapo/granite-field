package za.co.trademesh.modules.routing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Picked up by the application-wide @ConfigurationPropertiesScan over
 * za.co.trademesh, so no registration is needed here.
 *
 * @param providerTimeout how long a caller waits before the routing engine is
 *                        treated as unavailable and the fallback takes over
 */
@ConfigurationProperties("trademesh.routing")
public record RoutingProperties(Duration providerTimeout) {

    public RoutingProperties {
        if (providerTimeout == null) {
            providerTimeout = Duration.ofSeconds(5);
        }
    }
}
