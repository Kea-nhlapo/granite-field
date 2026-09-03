package za.co.trademesh.modules.notification.application;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.notifications.mobile")
public record MobileNotificationProperties(String provider, int maxDeliveryAttempts) {

    public MobileNotificationProperties {
        provider = provider == null || provider.isBlank()
                ? "local"
                : provider.strip().toLowerCase(Locale.ROOT);
        maxDeliveryAttempts = maxDeliveryAttempts < 1 ? 3 : maxDeliveryAttempts;
    }
}
