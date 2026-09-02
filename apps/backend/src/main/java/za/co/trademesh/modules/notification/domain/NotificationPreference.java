package za.co.trademesh.modules.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationPreference(
        UUID userId,
        NotificationCategory category,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean whatsappEnabled,
        Instant updatedAt) {}
