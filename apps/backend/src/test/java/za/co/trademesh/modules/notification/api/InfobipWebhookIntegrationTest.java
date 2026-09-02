package za.co.trademesh.modules.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.modules.notification.application.NotificationDataProtector;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@TestPropertySource(
        properties = "trademesh.notifications.mobile.providers.infobip.webhook-hmac-secret=test-only-webhook-secret")
class InfobipWebhookIntegrationTest extends PostgresIntegrationTest {

    private static final String SECRET = "test-only-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationDataProtector dataProtector;

    @BeforeEach
    @AfterEach
    void cleanState() {
        jdbcTemplate.update("DELETE FROM mobile_status_observation");
        jdbcTemplate.update("DELETE FROM mobile_delivery_attempt");
        jdbcTemplate.update("DELETE FROM mobile_notification_template_data");
        jdbcTemplate.update("DELETE FROM mobile_notification");
    }

    @Test
    void authenticatesReplaysAndMonotonicallyAppliesDeliveryAndSeenReports() throws Exception {
        UUID notificationId = UUID.randomUUID();
        insertQueued(notificationId, "provider-message-1");
        String delivered = """
            {"results":[{"messageId":"provider-message-1","callbackData":"%s",
            "status":{"groupName":"DELIVERED","name":"DELIVERED_TO_HANDSET"},
            "doneAt":"2026-09-02T17:30:00Z"}]}
            """.formatted(notificationId);

        mockMvc.perform(post("/api/notification-provider/infobip/delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(delivered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INFOBIP_SIGNATURE_INVALID"));

        sendSigned("/api/notification-provider/infobip/delivery", delivered);
        sendSigned("/api/notification-provider/infobip/delivery", delivered);
        assertThat(notificationStatus(notificationId)).isEqualTo("DELIVERED");
        assertThat(observationCount(notificationId)).isOne();

        String seen = """
            {"messageId":"provider-message-1","callbackData":"%s","seenAt":"2026-09-02T17:31:00Z"}
            """.formatted(notificationId);
        sendSigned("/api/notification-provider/infobip/seen", seen);
        assertThat(notificationStatus(notificationId)).isEqualTo("READ");

        String lateSent = """
            {"messageId":"provider-message-1","callbackData":"%s","status":{"groupName":"SENT"}}
            """.formatted(notificationId);
        sendSigned("/api/notification-provider/infobip/delivery", lateSent);
        assertThat(notificationStatus(notificationId)).isEqualTo("READ");
        assertThat(observationCount(notificationId)).isEqualTo(3);
    }

    private void sendSigned(String path, String body) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature", signature(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    private void insertQueued(UUID id, String providerMessageId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-02T17:00:00Z");
        jdbcTemplate.update(
                """
            INSERT INTO mobile_notification (
                id, idempotency_key, request_fingerprint, channel, category,
                template_key, template_version, protected_recipient, recipient_last_four,
                status, provider_key, provider_message_id, created_at, submitted_at, updated_at
            ) VALUES (?, ?, ?, 'SMS', 'SHIPMENT_UPDATE', 'capacity-match-found', 1,
                      ?, '4567', 'QUEUED', 'infobip', ?, ?, ?, ?)
            """,
                id,
                "callback-test:" + id,
                "a".repeat(64),
                dataProtector.protect("+27821234567"),
                providerMessageId,
                now,
                now,
                now);
    }

    private String notificationStatus(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM mobile_notification WHERE id = ?", String.class, notificationId);
    }

    private int observationCount(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mobile_status_observation WHERE notification_id = ?",
                Integer.class,
                notificationId);
    }

    private static String signature(String body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(hmac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
