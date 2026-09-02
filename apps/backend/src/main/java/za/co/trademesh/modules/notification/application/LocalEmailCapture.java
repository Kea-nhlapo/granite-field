package za.co.trademesh.modules.notification.application;

import java.time.Instant;
import java.util.List;

public interface LocalEmailCapture {

    List<CapturedEmail> capturedEmails();

    void clear();

    record CapturedEmail(
            String providerMessageId,
            String idempotencyKey,
            String fromAddress,
            String recipientEmail,
            String subject,
            String textBody,
            Instant capturedAt) {}
}
