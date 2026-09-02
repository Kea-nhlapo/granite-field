package za.co.trademesh.modules.notification.application;

import java.time.Instant;
import java.util.List;
import za.co.trademesh.modules.notification.domain.MobileChannel;

public interface LocalMobileCapture {

    List<CapturedMessage> capturedMessages();

    void clear();

    record CapturedMessage(
            String providerMessageId,
            String idempotencyKey,
            String recipientPhone,
            MobileChannel channel,
            String body,
            Instant capturedAt) {}
}
