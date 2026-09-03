package za.co.trademesh.modules.notification.infrastructure;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import za.co.trademesh.modules.notification.application.InfobipNotificationProperties;
import za.co.trademesh.modules.notification.application.MobileDeliveryProvider;
import za.co.trademesh.modules.notification.application.MobileProviderException;
import za.co.trademesh.modules.notification.application.NotificationTemplates;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;

@Component
@ConditionalOnExpression(
        "'${TRADEMESH_CI_READINESS:false}' != 'true' and '${trademesh.notifications.mobile.provider:local}' == 'infobip'")
class InfobipMobileDeliveryProvider implements MobileDeliveryProvider {

    private static final Set<String> REQUIRED_TEMPLATES = Set.of(
            NotificationTemplates.CAPACITY_MATCH_FOUND,
            NotificationTemplates.HANDOVER_CONFIRMATION_ACCEPTED,
            NotificationTemplates.HANDOVER_FINALIZED_CLEAN,
            NotificationTemplates.HANDOVER_FINALIZED_DISPUTED,
            NotificationTemplates.ESCROW_RELEASED,
            NotificationTemplates.DELIVERY_CONFIRMATION);

    private final RestClient client;
    private final InfobipNotificationProperties properties;

    InfobipMobileDeliveryProvider(RestClient infobipRestClient, InfobipNotificationProperties properties) {
        validate(properties);
        this.client = infobipRestClient;
        this.properties = properties;
    }

    @Override
    public String providerKey() {
        return "infobip";
    }

    @Override
    public SubmissionResult deliver(MobileMessage message) {
        try {
            ProviderResponse response = client.post()
                    .uri("/messages-api/1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(message))
                    .retrieve()
                    .body(ProviderResponse.class);
            ProviderMessage submitted = firstSubmission(response);
            MobileNotificationStatus status = submittedStatus(submitted.status());
            return new SubmissionResult(submitted.messageId().strip(), status);
        } catch (RestClientResponseException failure) {
            boolean retryable = failure.getStatusCode().value() == 429
                    || failure.getStatusCode().is5xxServerError();
            throw new MobileProviderException(
                    "INFOBIP_HTTP_" + failure.getStatusCode().value(),
                    retryable ? "Infobip temporarily rejected the submission." : "Infobip rejected the submission.",
                    retryable
                            ? MobileProviderException.FailureKind.RETRYABLE
                            : MobileProviderException.FailureKind.PERMANENT,
                    failure);
        } catch (ResourceAccessException failure) {
            throw new MobileProviderException(
                    "INFOBIP_SUBMISSION_UNKNOWN",
                    "The Infobip submission outcome is unknown.",
                    MobileProviderException.FailureKind.SUBMISSION_UNKNOWN,
                    failure);
        } catch (MobileProviderException failure) {
            throw failure;
        } catch (RestClientException failure) {
            throw new MobileProviderException(
                    "INFOBIP_SUBMISSION_UNKNOWN",
                    "The Infobip submission outcome is unknown.",
                    MobileProviderException.FailureKind.SUBMISSION_UNKNOWN,
                    failure);
        }
    }

    @Override
    public Optional<ReconciliationResult> reconcile(java.util.UUID notificationId) {
        try {
            ProviderResponse response = client.get()
                    .uri(builder -> builder.path("/messages-api/1/reports")
                            .queryParam("messageID", notificationId)
                            .build())
                    .retrieve()
                    .body(ProviderResponse.class);
            List<ProviderMessage> reports =
                    response == null || response.results() == null ? List.of() : response.results();
            if (reports.isEmpty()) {
                return Optional.empty();
            }
            ProviderMessage report = reports.getFirst();
            if (report == null
                    || report.messageId() == null
                    || report.messageId().isBlank()) {
                return Optional.empty();
            }
            String providerStatus = report.status() == null || report.status().groupName() == null
                    ? "ACCEPTED"
                    : report.status().groupName().strip();
            return Optional.of(new ReconciliationResult(
                    report.messageId().strip(),
                    providerStatus,
                    reportStatus(providerStatus),
                    instant(report.doneAt())));
        } catch (RestClientResponseException failure) {
            boolean retryable = failure.getStatusCode().value() == 429
                    || failure.getStatusCode().is5xxServerError();
            throw new MobileProviderException(
                    "INFOBIP_REPORT_HTTP_" + failure.getStatusCode().value(),
                    "The Infobip delivery report is temporarily unavailable.",
                    retryable
                            ? MobileProviderException.FailureKind.RETRYABLE
                            : MobileProviderException.FailureKind.PERMANENT,
                    failure);
        } catch (RestClientException failure) {
            throw new MobileProviderException(
                    "INFOBIP_REPORT_UNAVAILABLE",
                    "The Infobip delivery report is temporarily unavailable.",
                    MobileProviderException.FailureKind.RETRYABLE,
                    failure);
        }
    }

    private Map<String, Object> payload(MobileMessage message) {
        String sender = message.channel() == MobileChannel.SMS ? properties.smsSender() : properties.whatsappSender();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "TEXT");
        if (message.channel() == MobileChannel.SMS) {
            body.put("text", message.text());
        } else {
            for (int index = 0; index < message.whatsappParameters().size(); index++) {
                body.put(
                        Integer.toString(index + 1),
                        message.whatsappParameters().get(index));
            }
        }

        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("channel", message.channel().name());
        outbound.put("sender", providerAddress(sender));
        outbound.put("destinations", List.of(Map.of("to", providerAddress(message.recipientPhone()))));
        outbound.put("content", Map.of("body", body));
        outbound.put("messageId", message.notificationId().toString());
        outbound.put("callbackData", message.notificationId().toString());
        outbound.put("options", Map.of("adaptationMode", false));
        if (message.channel() == MobileChannel.WHATSAPP) {
            outbound.put(
                    "template",
                    Map.of(
                            "templateName",
                            properties.whatsappTemplate(message.templateKey(), message.templateVersion()),
                            "language",
                            message.whatsappLanguage()));
        }
        return Map.of("messages", List.of(outbound));
    }

    private static ProviderMessage firstSubmission(ProviderResponse response) {
        if (response == null
                || response.messages() == null
                || response.messages().size() != 1
                || response.messages().getFirst() == null
                || response.messages().getFirst().messageId() == null
                || response.messages().getFirst().messageId().isBlank()) {
            throw new MobileProviderException(
                    "INFOBIP_RESPONSE_INVALID",
                    "Infobip returned an invalid submission response.",
                    MobileProviderException.FailureKind.SUBMISSION_UNKNOWN);
        }
        return response.messages().getFirst();
    }

    private static MobileNotificationStatus submittedStatus(ProviderStatus status) {
        if (status == null || status.groupName() == null) {
            return MobileNotificationStatus.ACCEPTED;
        }
        return switch (status.groupName().strip().toUpperCase(java.util.Locale.ROOT)) {
            case "PENDING", "QUEUED" -> MobileNotificationStatus.QUEUED;
            case "SENT" -> MobileNotificationStatus.SENT;
            case "REJECTED", "FAILED", "EXPIRED" ->
                throw new MobileProviderException(
                        "INFOBIP_SUBMISSION_REJECTED",
                        "Infobip rejected the submission.",
                        MobileProviderException.FailureKind.PERMANENT);
            default -> MobileNotificationStatus.ACCEPTED;
        };
    }

    private static MobileNotificationStatus reportStatus(String raw) {
        String status = raw.toUpperCase(java.util.Locale.ROOT);
        if (status.contains("DELIVERED")) {
            return MobileNotificationStatus.DELIVERED;
        }
        if (status.contains("READ") || status.contains("SEEN")) {
            return MobileNotificationStatus.READ;
        }
        if (status.contains("SENT")) {
            return MobileNotificationStatus.SENT;
        }
        if (status.contains("PENDING") || status.contains("QUEUED")) {
            return MobileNotificationStatus.QUEUED;
        }
        if (status.contains("EXPIRED")) {
            return MobileNotificationStatus.EXPIRED;
        }
        if (status.contains("REJECTED")) {
            return MobileNotificationStatus.REJECTED;
        }
        if (status.contains("FAILED") || status.contains("UNDELIVERABLE")) {
            return MobileNotificationStatus.FAILED;
        }
        return MobileNotificationStatus.ACCEPTED;
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException invalidInstant) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (RuntimeException invalidOffset) {
                try {
                    return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]xx"))
                            .toInstant();
                } catch (RuntimeException invalidCompactOffset) {
                    return null;
                }
            }
        }
    }

    private static String providerAddress(String value) {
        return value.startsWith("+") ? value.substring(1) : value;
    }

    static void validate(InfobipNotificationProperties properties) {
        URI base;
        try {
            base = URI.create(properties.baseUrl());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Infobip base URL must be valid HTTPS", invalid);
        }
        if (!"https".equalsIgnoreCase(base.getScheme())
                || base.getHost() == null
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null) {
            throw new IllegalStateException("Infobip base URL must use HTTPS");
        }
        if (properties.apiKey().isBlank()
                || properties.smsSender().isBlank()
                || properties.whatsappSender().isBlank()
                || properties.webhookHmacSecret().isBlank()) {
            throw new IllegalStateException("Infobip credentials, senders, and webhook HMAC secret are required");
        }
        for (String template : REQUIRED_TEMPLATES) {
            if (properties.whatsappTemplate(template, 1).isBlank()) {
                throw new IllegalStateException("Every Infobip WhatsApp template mapping is required");
            }
        }
    }

    private record ProviderResponse(List<ProviderMessage> messages, List<ProviderMessage> results) {}

    private record ProviderMessage(String messageId, ProviderStatus status, String doneAt) {}

    private record ProviderStatus(String groupName) {}
}
