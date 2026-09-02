package za.co.trademesh.modules.notification.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.notification.domain.EmailNotification;
import za.co.trademesh.modules.notification.domain.EmailNotificationStatus;
import za.co.trademesh.modules.notification.domain.NotificationCategory;
import za.co.trademesh.modules.notification.domain.NotificationRepository;
import za.co.trademesh.shared.events.outbox.OutboxSubmitter;

@Service
public class EmailNotificationService implements NotificationRequests {

    private static final int MAX_EMAIL_LENGTH = 320;
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final NotificationRepository repository;
    private final EmailTemplateCatalog templates;
    private final OutboxSubmitter outbox;
    private final Clock clock;

    public EmailNotificationService(
            NotificationRepository repository, EmailTemplateCatalog templates, OutboxSubmitter outbox, Clock clock) {
        this.repository = repository;
        this.templates = templates;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID requestEmail(EmailRequest request) {
        ValidatedRequest validated = validate(request);
        var existing = repository.findByIdempotencyKey(validated.idempotencyKey());
        if (existing.isPresent()) {
            return sameRequest(existing.get(), validated.fingerprint()).id();
        }

        boolean suppressed = !validated.requiredDelivery()
                && validated.recipientUserId() != null
                && !repository.emailEnabled(validated.recipientUserId(), validated.category());
        EmailNotification notification = new EmailNotification(
                UUID.randomUUID(),
                validated.idempotencyKey(),
                validated.fingerprint(),
                validated.recipientEmail(),
                validated.recipientUserId(),
                validated.category(),
                validated.templateKey(),
                validated.templateVersion(),
                validated.templateData(),
                suppressed ? EmailNotificationStatus.SUPPRESSED : EmailNotificationStatus.PENDING,
                clock.instant(),
                null,
                null);
        if (!repository.saveNotification(notification)) {
            return sameRequest(
                            repository
                                    .findByIdempotencyKey(validated.idempotencyKey())
                                    .orElseThrow(() -> new IllegalStateException("Notification idempotency conflict")),
                            validated.fingerprint())
                    .id();
        }
        if (!suppressed) {
            outbox.submit(
                    EmailDeliveryRequested.TYPE,
                    notification.id().toString(),
                    new EmailDeliveryRequested(notification.id()),
                    EmailDeliveryRequested.SCHEMA_VERSION);
        }
        return notification.id();
    }

    private ValidatedRequest validate(EmailRequest request) {
        if (request == null
                || request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()
                || request.idempotencyKey().length() > 200
                || request.templateKey() == null
                || request.templateKey().isBlank()
                || request.templateKey().length() > 100
                || request.templateVersion() < 1) {
            throw new IllegalArgumentException("Invalid email notification request");
        }
        String email = normalizeEmail(request.recipientEmail());
        NotificationCategory category;
        try {
            category = NotificationCategory.valueOf(request.category());
        } catch (RuntimeException invalidCategory) {
            throw new IllegalArgumentException("Invalid notification category", invalidCategory);
        }
        Map<String, String> data = Map.copyOf(request.templateData());
        templates.render(request.templateKey(), request.templateVersion(), data);
        String fingerprint = fingerprint(
                request.idempotencyKey(),
                email,
                request.recipientUserId(),
                category,
                request.templateKey(),
                request.templateVersion(),
                data,
                request.requiredDelivery());
        return new ValidatedRequest(
                request.idempotencyKey().strip(),
                email,
                request.recipientUserId(),
                category,
                request.templateKey().strip(),
                request.templateVersion(),
                data,
                request.requiredDelivery(),
                fingerprint);
    }

    private static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            throw new IllegalArgumentException("Invalid notification recipient");
        }
        String normalized = rawEmail.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH
                || !SIMPLE_EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid notification recipient");
        }
        return normalized;
    }

    private static EmailNotification sameRequest(EmailNotification existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IllegalStateException("Notification idempotency key was reused for different content");
        }
        return existing;
    }

    private static String fingerprint(
            String idempotencyKey,
            String recipientEmail,
            UUID recipientUserId,
            NotificationCategory category,
            String templateKey,
            int templateVersion,
            Map<String, String> templateData,
            boolean requiredDelivery) {
        StringBuilder canonical = new StringBuilder()
                .append(idempotencyKey.strip())
                .append('|')
                .append(recipientEmail)
                .append('|')
                .append(recipientUserId)
                .append('|')
                .append(category)
                .append('|')
                .append(templateKey.strip())
                .append('|')
                .append(templateVersion)
                .append('|')
                .append(requiredDelivery);
        new TreeMap<>(templateData).forEach((key, value) -> canonical
                .append('|')
                .append(key.length())
                .append(':')
                .append(key)
                .append('=')
                .append(value == null ? -1 : value.length())
                .append(':')
                .append(value));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private record ValidatedRequest(
            String idempotencyKey,
            String recipientEmail,
            UUID recipientUserId,
            NotificationCategory category,
            String templateKey,
            int templateVersion,
            Map<String, String> templateData,
            boolean requiredDelivery,
            String fingerprint) {}
}
