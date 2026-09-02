package za.co.trademesh.modules.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record MobileDeliveryAttempt(
        UUID id,
        UUID notificationId,
        UUID outboxMessageId,
        int attemptNumber,
        String providerKey,
        MobileDeliveryAttemptStatus status,
        String providerMessageId,
        String failureCode,
        String failureMessage,
        Instant startedAt,
        Instant completedAt) {}
