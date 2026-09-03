package za.co.trademesh.modules.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record MobileStatusObservation(
        UUID id,
        UUID notificationId,
        String callbackFingerprint,
        String providerKey,
        String providerMessageId,
        String providerStatus,
        Instant observedAt,
        Instant receivedAt) {}
