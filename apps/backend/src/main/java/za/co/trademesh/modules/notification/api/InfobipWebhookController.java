package za.co.trademesh.modules.notification.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.modules.notification.application.InfobipStatusService;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;
import za.co.trademesh.modules.notification.infrastructure.InfobipWebhookVerifier;

@RestController
@RequestMapping("/api/notification-provider/infobip")
public class InfobipWebhookController {

    private static final int MAXIMUM_BODY_BYTES = 1_048_576;

    private final InfobipWebhookVerifier verifier;
    private final InfobipStatusService statuses;
    private final ObjectMapper objectMapper;

    public InfobipWebhookController(
            InfobipWebhookVerifier verifier, InfobipStatusService statuses, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.statuses = statuses;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/delivery")
    @ResponseStatus(HttpStatus.OK)
    void delivery(
            @RequestBody byte[] body, @RequestHeader(name = "X-Hub-Signature", required = false) String signature) {
        process(body, signature, false);
    }

    @PostMapping("/seen")
    @ResponseStatus(HttpStatus.OK)
    void seen(@RequestBody byte[] body, @RequestHeader(name = "X-Hub-Signature", required = false) String signature) {
        process(body, signature, true);
    }

    private void process(byte[] body, String signature, boolean seen) {
        if (body == null || body.length == 0) {
            throw NotificationWebhookException.invalid();
        }
        if (body.length > MAXIMUM_BODY_BYTES) {
            throw NotificationWebhookException.tooLarge();
        }
        if (!verifier.valid(body, signature)) {
            throw NotificationWebhookException.unauthorized();
        }
        List<JsonNode> reports = reports(parse(body));
        if (reports.isEmpty()) {
            throw NotificationWebhookException.invalid();
        }
        for (int index = 0; index < reports.size(); index++) {
            JsonNode report = reports.get(index);
            String messageId = text(report, "messageId");
            if (messageId == null || messageId.length() > 200) {
                throw NotificationWebhookException.invalid();
            }
            UUID notificationId = uuid(text(report, "callbackData"));
            if (notificationId == null) {
                notificationId = uuid(messageId);
            }
            String providerStatus = seen ? "SEEN" : providerStatus(report);
            MobileNotificationStatus status = seen ? MobileNotificationStatus.READ : mapStatus(providerStatus);
            statuses.record(new InfobipStatusService.StatusUpdate(
                    notificationId,
                    messageId,
                    providerStatus,
                    status,
                    verifier.fingerprint(body, seen ? "seen" : "delivery", index),
                    observedAt(report)));
        }
    }

    private JsonNode parse(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException invalid) {
            throw NotificationWebhookException.invalid();
        }
    }

    private static List<JsonNode> reports(JsonNode root) {
        List<JsonNode> reports = new ArrayList<>();
        if (root == null || root.isNull()) {
            return reports;
        }
        JsonNode collection =
                root.isArray() ? root : root.path("results").isArray() ? root.path("results") : root.path("messages");
        if (collection.isArray()) {
            collection.forEach(reports::add);
        } else if (root.isObject()) {
            reports.add(root);
        }
        return reports;
    }

    private static String providerStatus(JsonNode report) {
        JsonNode status = report.path("status");
        String value = text(status, "groupName");
        if (value == null) {
            value = text(status, "name");
        }
        if (value == null && status.isTextual()) {
            value = status.asText();
        }
        if (value == null || value.isBlank()) {
            throw NotificationWebhookException.invalid();
        }
        return value.strip();
    }

    private static MobileNotificationStatus mapStatus(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.contains("DELIVERED")) {
            return MobileNotificationStatus.DELIVERED;
        }
        if (normalized.contains("READ") || normalized.contains("SEEN")) {
            return MobileNotificationStatus.READ;
        }
        if (normalized.contains("SENT")) {
            return MobileNotificationStatus.SENT;
        }
        if (normalized.contains("PENDING") || normalized.contains("QUEUED")) {
            return MobileNotificationStatus.QUEUED;
        }
        if (normalized.contains("ACCEPTED")) {
            return MobileNotificationStatus.ACCEPTED;
        }
        if (normalized.contains("EXPIRED")) {
            return MobileNotificationStatus.EXPIRED;
        }
        if (normalized.contains("REJECTED")) {
            return MobileNotificationStatus.REJECTED;
        }
        if (normalized.contains("FAILED") || normalized.contains("UNDELIVERABLE")) {
            return MobileNotificationStatus.FAILED;
        }
        throw NotificationWebhookException.invalid();
    }

    private static Instant observedAt(JsonNode report) {
        for (String field : List.of("doneAt", "seenAt", "sentAt", "timestamp")) {
            String value = text(report, field);
            if (value != null) {
                try {
                    return Instant.parse(value);
                } catch (RuntimeException ignored) {
                    try {
                        return OffsetDateTime.parse(value).toInstant();
                    } catch (RuntimeException alsoInvalid) {
                        try {
                            return OffsetDateTime.parse(
                                            value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]xx"))
                                    .toInstant();
                        } catch (RuntimeException ignoredInvalidOffset) {
                            // Try another known timestamp field, then fall back to receipt time.
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().strip() : null;
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
