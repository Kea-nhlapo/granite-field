package za.co.trademesh.modules.telemetry.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.tracking")
public record TrackingProperties(
        Duration streamTimeout,
        double backhaulRadiusMetres,
        Duration backhaulTimeWindow,
        int backhaulCandidateLimit,
        double backhaulDistanceWeight,
        double backhaulTrustWeight) {

    public TrackingProperties {
        if (streamTimeout == null || streamTimeout.isZero() || streamTimeout.isNegative()) {
            streamTimeout = Duration.ofMinutes(30);
        }
        if (!Double.isFinite(backhaulRadiusMetres) || backhaulRadiusMetres <= 0) {
            backhaulRadiusMetres = 30_000;
        }
        if (backhaulTimeWindow == null || backhaulTimeWindow.isZero() || backhaulTimeWindow.isNegative()) {
            backhaulTimeWindow = Duration.ofHours(6);
        }
        if (backhaulCandidateLimit < 1 || backhaulCandidateLimit > 100) {
            backhaulCandidateLimit = 20;
        }
        if (backhaulDistanceWeight < 0
                || backhaulTrustWeight < 0
                || backhaulDistanceWeight + backhaulTrustWeight <= 0) {
            backhaulDistanceWeight = 0.70;
            backhaulTrustWeight = 0.30;
        }
    }
}
