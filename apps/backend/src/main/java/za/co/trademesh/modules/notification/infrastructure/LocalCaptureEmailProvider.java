package za.co.trademesh.modules.notification.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.notification.application.EmailDeliveryProvider;
import za.co.trademesh.modules.notification.application.LocalEmailCapture;

@Component
@ConditionalOnProperty(
        prefix = "trademesh.notifications.email",
        name = "provider",
        havingValue = "local",
        matchIfMissing = true)
class LocalCaptureEmailProvider implements EmailDeliveryProvider, LocalEmailCapture {

    private final ConcurrentHashMap<String, CapturedEmail> captured = new ConcurrentHashMap<>();
    private final Clock clock;

    LocalCaptureEmailProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String providerKey() {
        return "local-capture";
    }

    @Override
    public DeliveryResult deliver(EmailMessage message) {
        CapturedEmail email = captured.computeIfAbsent(
                message.idempotencyKey(),
                ignored -> new CapturedEmail(
                        providerMessageId(message.idempotencyKey()),
                        message.idempotencyKey(),
                        message.fromAddress(),
                        message.recipientEmail(),
                        message.subject(),
                        message.textBody(),
                        clock.instant()));
        return new DeliveryResult(email.providerMessageId());
    }

    @Override
    public List<CapturedEmail> capturedEmails() {
        return captured.values().stream()
                .sorted(Comparator.comparing(CapturedEmail::capturedAt))
                .toList();
    }

    @Override
    public void clear() {
        captured.clear();
    }

    private static String providerMessageId(String idempotencyKey) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return "local-" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
