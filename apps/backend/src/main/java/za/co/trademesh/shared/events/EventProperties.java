package za.co.trademesh.shared.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param source component name stamped onto every envelope, so a message can
 *               be traced back to the deployment that produced it
 */
@ConfigurationProperties("trademesh.events")
public record EventProperties(String source) {

    public EventProperties {
        if (source == null || source.isBlank()) {
            source = "trademesh-backend";
        }
    }
}
