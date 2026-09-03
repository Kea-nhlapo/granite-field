package za.co.trademesh.modules.notification.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MobileNotificationRepository {

    boolean saveNotification(MobileNotification notification);

    Optional<MobileNotification> findNotification(UUID notificationId);

    Optional<MobileNotification> findNotificationForUpdate(UUID notificationId);

    Optional<MobileNotification> findByIdempotencyKey(String idempotencyKey);

    Optional<MobileNotification> findByProviderMessageId(String providerKey, String providerMessageId);

    boolean saveAttempt(MobileDeliveryAttempt attempt);

    Optional<MobileDeliveryAttempt> findAttempt(UUID notificationId, UUID outboxMessageId, int attemptNumber);

    Optional<MobileDeliveryAttempt> findLatestStartedAttempt(UUID notificationId);

    boolean markSubmitting(UUID notificationId, Instant updatedAt);

    void markSubmitted(
            UUID notificationId,
            UUID attemptId,
            String providerKey,
            String providerMessageId,
            MobileNotificationStatus status,
            Instant completedAt);

    void markFailed(
            UUID notificationId,
            UUID attemptId,
            String failureCode,
            String failureMessage,
            boolean finalFailure,
            Instant completedAt);

    void markSubmissionUnknown(
            UUID notificationId,
            UUID attemptId,
            String providerKey,
            String failureCode,
            String failureMessage,
            Instant completedAt);

    boolean saveObservation(MobileStatusObservation observation);

    void updateStatus(
            UUID notificationId,
            String providerKey,
            String providerMessageId,
            MobileNotificationStatus status,
            Instant observedAt,
            Instant updatedAt);
}
