package za.co.trademesh.modules.notification.application;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.shared.events.outbox.OutboxSubmitter;

@Service
class MobileNotificationService implements MobileNotificationRequests {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final int MAX_MESSAGE_LENGTH = 1_000;
    private final NotificationDataProtector dataProtector;
    private final OutboxSubmitter outbox;

    MobileNotificationService(NotificationDataProtector dataProtector, OutboxSubmitter outbox) {
        this.dataProtector = dataProtector;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public void requestMobile(MobileRequest request) {
        if (request == null
                || request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()
                || request.idempotencyKey().length() > 200
                || request.recipientPhone() == null
                || !E164.matcher(request.recipientPhone().strip()).matches()
                || request.channel() == null
                || request.message() == null
                || request.message().isBlank()
                || request.message().length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Invalid mobile notification request");
        }
        String key = request.idempotencyKey().strip();
        outbox.submit(
                MobileDeliveryRequested.TYPE,
                key,
                new MobileDeliveryRequested(
                        key,
                        dataProtector.protect(request.recipientPhone().strip()),
                        dataProtector.protect(request.message().strip()),
                        request.channel()),
                MobileDeliveryRequested.SCHEMA_VERSION);
    }
}
