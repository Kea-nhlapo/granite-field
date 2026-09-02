package za.co.trademesh.modules.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.notifications.email")
public record NotificationEmailProperties(
        String provider, int maxDeliveryAttempts, String endpoint, String apiKey, String fromAddress) {

    public NotificationEmailProperties {
        if (provider == null || provider.isBlank()) {
            provider = "local";
        } else {
            provider = provider.strip().toLowerCase(java.util.Locale.ROOT);
        }
        if (maxDeliveryAttempts <= 0) {
            maxDeliveryAttempts = 3;
        }
        endpoint = endpoint == null ? "" : endpoint.strip();
        apiKey = apiKey == null ? "" : apiKey.strip();
        if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = "no-reply@trademesh.local";
        } else {
            fromAddress = fromAddress.strip().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
