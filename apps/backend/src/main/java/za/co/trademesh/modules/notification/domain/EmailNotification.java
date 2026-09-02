package za.co.trademesh.modules.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EmailNotification(
        UUID id,
        String idempotencyKey,
        String requestFingerprint,
        String recipientEmail,
        UUID recipientUserId,
        NotificationCategory category,
        String templateKey,
        int templateVersion,
        Map<String, String> templateData,
        EmailNotificationStatus status,
        Instant createdAt,
        Instant sentAt,
        Instant failedAt) {

    public EmailNotification {
        templateData = Map.copyOf(templateData);
    }

    public boolean terminal() {
        return status == EmailNotificationStatus.SENT
                || status == EmailNotificationStatus.FAILED
                || status == EmailNotificationStatus.SUPPRESSED;
    }
}
