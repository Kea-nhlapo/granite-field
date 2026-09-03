package za.co.trademesh.modules.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import za.co.trademesh.modules.notification.domain.NotificationContactPoint;

public final class NotificationContactContracts {

    private NotificationContactContracts() {}

    public record SavePhoneRequest(
            @NotBlank String phoneNumber,
            @NotNull Boolean smsConsent,
            @NotNull Boolean whatsappConsent) {}

    public record PhoneResponse(
            boolean configured,
            String maskedPhone,
            Instant smsConsentedAt,
            Instant whatsappConsentedAt,
            Instant updatedAt) {

        static PhoneResponse empty() {
            return new PhoneResponse(false, null, null, null, null);
        }

        static PhoneResponse from(NotificationContactPoint contact) {
            return new PhoneResponse(
                    true,
                    "********" + contact.phoneLastFour(),
                    contact.smsConsentedAt(),
                    contact.whatsappConsentedAt(),
                    contact.updatedAt());
        }
    }
}
