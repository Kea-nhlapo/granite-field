package za.co.trademesh.modules.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MobileNotification(
        UUID id,
        String idempotencyKey,
        String requestFingerprint,
        UUID recipientUserId,
        MobileChannel channel,
        NotificationCategory category,
        String templateKey,
        int templateVersion,
        String recipientPhone,
        Map<String, String> templateData,
        MobileNotificationStatus status,
        String providerKey,
        String providerMessageId,
        Instant createdAt,
        Instant submittedAt,
        Instant sentAt,
        Instant deliveredAt,
        Instant readAt,
        Instant failedAt,
        Instant updatedAt) {

    public MobileNotification {
        templateData = Map.copyOf(templateData);
    }

    @Override
    public String toString() {
        return "MobileNotification[id=" + id + ", idempotencyKey=" + idempotencyKey + ", requestFingerprint="
                + requestFingerprint + ", recipientUserId=" + recipientUserId + ", channel=" + channel
                + ", category=" + category + ", templateKey=" + templateKey + ", templateVersion=" + templateVersion
                + ", recipientPhone=<redacted>, templateData=<redacted>, status=" + status + ", providerKey="
                + providerKey + ", providerMessageId=" + providerMessageId + ", createdAt=" + createdAt
                + ", submittedAt=" + submittedAt + ", sentAt=" + sentAt + ", deliveredAt=" + deliveredAt
                + ", readAt=" + readAt + ", failedAt=" + failedAt + ", updatedAt=" + updatedAt + "]";
    }
}
