package za.co.trademesh.modules.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    boolean saveNotification(EmailNotification notification);

    Optional<EmailNotification> findNotification(UUID notificationId);

    Optional<EmailNotification> findByIdempotencyKey(String idempotencyKey);

    boolean saveAttempt(EmailDeliveryAttempt attempt);

    Optional<EmailDeliveryAttempt> findAttempt(UUID notificationId, UUID outboxMessageId, int attemptNumber);

    void markSent(UUID notificationId, UUID attemptId, String providerMessageId, Instant completedAt);

    void markFailed(
            UUID notificationId,
            UUID attemptId,
            String failureCode,
            String failureMessage,
            boolean finalFailure,
            Instant completedAt);

    boolean emailEnabled(UUID userId, NotificationCategory category);

    NotificationPreference savePreference(NotificationPreference preference);

    List<NotificationPreference> findPreferences(UUID userId);
}
