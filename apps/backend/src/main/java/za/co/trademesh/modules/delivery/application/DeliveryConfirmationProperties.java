package za.co.trademesh.modules.delivery.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.delivery.confirmation")
public record DeliveryConfirmationProperties(Duration timeToLive, String confirmationBaseUrl) {

    public DeliveryConfirmationProperties {
        if (timeToLive == null || timeToLive.isZero() || timeToLive.isNegative()) {
            timeToLive = Duration.ofDays(2);
        }
        if (confirmationBaseUrl == null || confirmationBaseUrl.isBlank()) {
            confirmationBaseUrl = "http://localhost:5173/delivery/confirm";
        } else {
            confirmationBaseUrl = confirmationBaseUrl.strip().replaceAll("/+$", "");
        }
    }
}
