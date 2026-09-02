package za.co.trademesh.modules.document.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.documents.processing")
public record DocumentProcessingProperties(Duration claimTimeout) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    public DocumentProcessingProperties {
        if (claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()) {
            claimTimeout = DEFAULT_TIMEOUT;
        }
    }
}
