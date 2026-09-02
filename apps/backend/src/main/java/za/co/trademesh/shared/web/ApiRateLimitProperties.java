package za.co.trademesh.shared.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.web.rate-limits")
public record ApiRateLimitProperties(
        Duration window,
        int maximumTrackedClients,
        int login,
        int invitations,
        int uploads,
        int telemetry,
        int qrValidation) {

    public ApiRateLimitProperties {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate-limit window must be positive");
        }
        requirePositive(maximumTrackedClients, "maximum-tracked-clients");
        requirePositive(login, "login");
        requirePositive(invitations, "invitations");
        requirePositive(uploads, "uploads");
        requirePositive(telemetry, "telemetry");
        requirePositive(qrValidation, "qr-validation");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " rate limit must be positive");
        }
    }
}
