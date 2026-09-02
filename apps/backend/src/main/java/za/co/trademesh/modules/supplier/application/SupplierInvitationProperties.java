package za.co.trademesh.modules.supplier.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.supplier-invitations")
public record SupplierInvitationProperties(
        Duration timeToLive,
        int validationAttempts,
        Duration rateLimitWindow,
        int maxTrackedClients,
        String guestBaseUrl) {

    public SupplierInvitationProperties {
        if (timeToLive == null || timeToLive.isZero() || timeToLive.isNegative()) {
            timeToLive = Duration.ofDays(7);
        }
        if (validationAttempts <= 0) {
            validationAttempts = 30;
        }
        if (rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative()) {
            rateLimitWindow = Duration.ofMinutes(1);
        }
        if (maxTrackedClients <= 0) {
            maxTrackedClients = 10_000;
        }
        if (guestBaseUrl == null || guestBaseUrl.isBlank()) {
            guestBaseUrl = "http://localhost:5173/supplier-invitations/guest";
        } else {
            guestBaseUrl = guestBaseUrl.strip().replaceAll("/+$", "");
        }
    }
}
