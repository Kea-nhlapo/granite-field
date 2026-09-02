package za.co.trademesh.modules.access.application;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.access.turnstile")
public record TurnstileProperties(String provider, URI endpoint, String secretKey, String expectedHostname) {

    public TurnstileProperties {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Turnstile provider is required");
        }
        if (endpoint == null || !endpoint.isAbsolute()) {
            throw new IllegalArgumentException("Turnstile endpoint must be absolute");
        }
    }
}
