package za.co.trademesh.modules.notification.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.notification.domain.NotificationCategory;
import za.co.trademesh.modules.notification.domain.NotificationPreference;

public final class NotificationPreferenceContracts {

    private NotificationPreferenceContracts() {}

    public record UpdatePreferenceRequest(Boolean emailEnabled, Boolean smsEnabled, Boolean whatsappEnabled) {}

    public record PreferenceResponse(
            UUID userId,
            NotificationCategory category,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            Instant updatedAt) {

        static PreferenceResponse from(NotificationPreference preference) {
            return new PreferenceResponse(
                    preference.userId(),
                    preference.category(),
                    preference.emailEnabled(),
                    preference.smsEnabled(),
                    preference.whatsappEnabled(),
                    preference.updatedAt());
        }
    }

    public record PreferencesResponse(List<PreferenceResponse> preferences) {
        static PreferencesResponse from(List<NotificationPreference> preferences) {
            return new PreferencesResponse(
                    preferences.stream().map(PreferenceResponse::from).toList());
        }
    }
}
