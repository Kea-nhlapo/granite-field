package za.co.trademesh.modules.notification.infrastructure;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.notification.application.LocalMobileCapture;
import za.co.trademesh.modules.notification.application.MobileDeliveryProvider;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;

@Component
@ConditionalOnProperty(prefix = "trademesh.notifications.mobile", name = "provider", havingValue = "local")
class LocalCaptureMobileProvider implements MobileDeliveryProvider, LocalMobileCapture {

    private final ConcurrentHashMap<String, CapturedMessage> captured = new ConcurrentHashMap<>();
    private final Clock clock;

    LocalCaptureMobileProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String providerKey() {
        return "local-mobile-capture";
    }

    @Override
    public SubmissionResult deliver(MobileMessage message) {
        CapturedMessage saved = captured.computeIfAbsent(
                message.idempotencyKey(),
                key -> new CapturedMessage(
                        "local-mobile-" + Integer.toUnsignedString(key.hashCode()),
                        key,
                        message.recipientPhone(),
                        message.channel(),
                        message.text(),
                        clock.instant()));
        return new SubmissionResult(saved.providerMessageId(), MobileNotificationStatus.SENT);
    }

    @Override
    public List<CapturedMessage> capturedMessages() {
        return captured.values().stream()
                .sorted(Comparator.comparing(CapturedMessage::capturedAt))
                .toList();
    }

    @Override
    public void clear() {
        captured.clear();
    }
}
