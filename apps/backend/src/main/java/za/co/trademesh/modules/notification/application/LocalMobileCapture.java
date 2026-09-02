package za.co.trademesh.modules.notification.application;

import java.time.Instant;
import java.util.List;

public interface LocalMobileCapture {

    List<CapturedMessage> capturedMessages();

    void clear();

    record CapturedMessage(
            String providerMessageId,
            String idempotencyKey,
            String recipientPhone,
            MobileNotificationRequests.MobileChannel channel,
            String body,
            Instant capturedAt) {}
}
