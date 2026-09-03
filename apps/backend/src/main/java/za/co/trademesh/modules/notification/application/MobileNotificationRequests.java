package za.co.trademesh.modules.notification.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MobileNotificationRequests {

    List<UUID> requestUser(UserMobileRequest request);

    UUID requestDirect(DirectMobileRequest request);

    record UserMobileRequest(
            String eventType,
            UUID eventId,
            UUID recipientUserId,
            String category,
            String templateKey,
            int templateVersion,
            Map<String, String> templateData) {

        public UserMobileRequest {
            templateData = templateData == null ? Map.of() : Map.copyOf(templateData);
        }
    }

    record DirectMobileRequest(
            String idempotencyKey,
            String recipientPhone,
            String channel,
            String category,
            String templateKey,
            int templateVersion,
            Map<String, String> templateData) {

        public DirectMobileRequest {
            templateData = templateData == null ? Map.of() : Map.copyOf(templateData);
        }
    }
}
