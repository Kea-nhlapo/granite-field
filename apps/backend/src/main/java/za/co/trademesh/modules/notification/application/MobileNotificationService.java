package za.co.trademesh.modules.notification.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.MobileNotification;
import za.co.trademesh.modules.notification.domain.MobileNotificationRepository;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;
import za.co.trademesh.modules.notification.domain.NotificationCategory;
import za.co.trademesh.modules.notification.domain.NotificationContactPoint;
import za.co.trademesh.modules.notification.domain.NotificationRepository;
import za.co.trademesh.shared.events.outbox.OutboxSubmitter;

@Service
class MobileNotificationService implements MobileNotificationRequests {

    private final NotificationRepository contacts;
    private final MobileNotificationRepository notifications;
    private final MobileTemplateCatalog templates;
    private final OutboxSubmitter outbox;
    private final Clock clock;

    MobileNotificationService(
            NotificationRepository contacts,
            MobileNotificationRepository notifications,
            MobileTemplateCatalog templates,
            OutboxSubmitter outbox,
            Clock clock) {
        this.contacts = contacts;
        this.notifications = notifications;
        this.templates = templates;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<UUID> requestUser(UserMobileRequest request) {
        ValidatedUserRequest validated = validate(request);
        NotificationContactPoint contact =
                contacts.findContact(validated.recipientUserId()).orElse(null);
        return java.util.Arrays.stream(MobileChannel.values())
                .map(channel -> requestUserChannel(validated, contact, channel))
                .toList();
    }

    @Override
    @Transactional
    public UUID requestDirect(DirectMobileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Invalid direct mobile notification request");
        }
        String key = validKey(request.idempotencyKey());
        String phone = PhoneNumbers.normalize(request.recipientPhone());
        MobileChannel channel = channel(request.channel());
        NotificationCategory category = category(request.category());
        String templateKey = validTemplateKey(request.templateKey());
        Map<String, String> data = Map.copyOf(request.templateData());
        templates.render(templateKey, request.templateVersion(), data);
        String fingerprint =
                fingerprint(key, phone, null, channel, category, templateKey, request.templateVersion(), data);
        var now = clock.instant();
        return save(new MobileNotification(
                UUID.randomUUID(),
                key,
                fingerprint,
                null,
                channel,
                category,
                templateKey,
                request.templateVersion(),
                phone,
                data,
                MobileNotificationStatus.PENDING,
                null,
                null,
                now,
                null,
                null,
                null,
                null,
                null,
                now));
    }

    private UUID requestUserChannel(
            ValidatedUserRequest request, NotificationContactPoint contact, MobileChannel channel) {
        String key = userKey(request, channel);
        boolean deliver = contact != null
                && contact.consented(channel)
                && contacts.mobileEnabled(request.recipientUserId(), request.category(), channel);
        String phone = deliver ? contact.phoneNumber() : null;
        String fingerprint = fingerprint(
                key,
                null,
                request.recipientUserId(),
                channel,
                request.category(),
                request.templateKey(),
                request.templateVersion(),
                request.templateData());
        var now = clock.instant();
        return save(new MobileNotification(
                UUID.randomUUID(),
                key,
                fingerprint,
                request.recipientUserId(),
                channel,
                request.category(),
                request.templateKey(),
                request.templateVersion(),
                phone,
                request.templateData(),
                deliver ? MobileNotificationStatus.PENDING : MobileNotificationStatus.SUPPRESSED,
                null,
                null,
                now,
                null,
                null,
                null,
                null,
                null,
                now));
    }

    private UUID save(MobileNotification notification) {
        var existing = notifications.findByIdempotencyKey(notification.idempotencyKey());
        if (existing.isPresent()) {
            return sameRequest(existing.get(), notification.requestFingerprint())
                    .id();
        }
        if (!notifications.saveNotification(notification)) {
            return sameRequest(
                            notifications
                                    .findByIdempotencyKey(notification.idempotencyKey())
                                    .orElseThrow(() -> new IllegalStateException("Mobile notification conflict")),
                            notification.requestFingerprint())
                    .id();
        }
        if (notification.status() == MobileNotificationStatus.PENDING) {
            outbox.submit(
                    MobileDeliveryRequested.TYPE,
                    notification.id().toString(),
                    new MobileDeliveryRequested(notification.id()),
                    MobileDeliveryRequested.SCHEMA_VERSION);
        }
        return notification.id();
    }

    private ValidatedUserRequest validate(UserMobileRequest request) {
        if (request == null || request.eventId() == null || request.recipientUserId() == null) {
            throw new IllegalArgumentException("Invalid user mobile notification request");
        }
        String eventType = validKey(request.eventType());
        NotificationCategory category = category(request.category());
        String templateKey = validTemplateKey(request.templateKey());
        Map<String, String> data = Map.copyOf(request.templateData());
        templates.render(templateKey, request.templateVersion(), data);
        return new ValidatedUserRequest(
                eventType,
                request.eventId(),
                request.recipientUserId(),
                category,
                templateKey,
                request.templateVersion(),
                data);
    }

    private static String userKey(ValidatedUserRequest request, MobileChannel channel) {
        String canonical = String.join(
                ":",
                request.eventType(),
                request.eventId().toString(),
                request.recipientUserId().toString(),
                channel.name(),
                request.templateKey(),
                "v" + request.templateVersion());
        return "mobile:" + sha256(canonical);
    }

    private static String validKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 200) {
            throw new IllegalArgumentException("Invalid mobile notification idempotency value");
        }
        return value.strip();
    }

    private static String validTemplateKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 100) {
            throw new IllegalArgumentException("Invalid mobile notification template key");
        }
        return value.strip();
    }

    private static NotificationCategory category(String value) {
        try {
            return NotificationCategory.valueOf(value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid mobile notification category", invalid);
        }
    }

    private static MobileChannel channel(String value) {
        try {
            return MobileChannel.valueOf(value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid mobile notification channel", invalid);
        }
    }

    private static MobileNotification sameRequest(MobileNotification existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IllegalStateException("Mobile notification idempotency key was reused for different content");
        }
        return existing;
    }

    private static String fingerprint(
            String idempotencyKey,
            String recipientPhone,
            UUID recipientUserId,
            MobileChannel channel,
            NotificationCategory category,
            String templateKey,
            int templateVersion,
            Map<String, String> templateData) {
        StringBuilder canonical = new StringBuilder()
                .append(idempotencyKey)
                .append('|')
                .append(recipientPhone)
                .append('|')
                .append(recipientUserId)
                .append('|')
                .append(channel)
                .append('|')
                .append(category)
                .append('|')
                .append(templateKey)
                .append('|')
                .append(templateVersion);
        new TreeMap<>(templateData).forEach((key, value) -> canonical
                .append('|')
                .append(key.length())
                .append(':')
                .append(key)
                .append('=')
                .append(value.length())
                .append(':')
                .append(value));
        return sha256(canonical.toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    private record ValidatedUserRequest(
            String eventType,
            UUID eventId,
            UUID recipientUserId,
            NotificationCategory category,
            String templateKey,
            int templateVersion,
            Map<String, String> templateData) {}
}
