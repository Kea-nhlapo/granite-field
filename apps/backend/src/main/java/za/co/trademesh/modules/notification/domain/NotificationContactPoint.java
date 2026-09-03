package za.co.trademesh.modules.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationContactPoint(
        UUID userId,
        String phoneNumber,
        String phoneFingerprint,
        String phoneLastFour,
        Instant smsConsentedAt,
        Instant whatsappConsentedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean consented(MobileChannel channel) {
        return switch (channel) {
            case SMS -> smsConsentedAt != null;
            case WHATSAPP -> whatsappConsentedAt != null;
        };
    }

    @Override
    public String toString() {
        return "NotificationContactPoint[userId=" + userId
                + ", phoneNumber=<redacted>, phoneFingerprint=<redacted>, phoneLastFour=" + phoneLastFour
                + ", smsConsentedAt=" + smsConsentedAt + ", whatsappConsentedAt=" + whatsappConsentedAt
                + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
    }
}
