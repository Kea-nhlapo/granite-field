package za.co.trademesh.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.notification.domain.NotificationCategory;
import za.co.trademesh.shared.events.outbox.OutboxWorker;
import za.co.trademesh.support.PostgresIntegrationTest;

@TestPropertySource(properties = "trademesh.outbox.enabled=false")
class MobileNotificationServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private NotificationContactService contacts;

    @Autowired
    private NotificationPreferenceService preferences;

    @Autowired
    private MobileNotificationRequests notifications;

    @Autowired
    private LocalMobileCapture capture;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanState() {
        capture.clear();
        jdbcTemplate.update("DELETE FROM mobile_status_observation");
        jdbcTemplate.update("DELETE FROM mobile_delivery_attempt");
        jdbcTemplate.update("DELETE FROM mobile_notification_template_data");
        jdbcTemplate.update("DELETE FROM mobile_notification");
        jdbcTemplate.update("DELETE FROM notification_contact_point");
        jdbcTemplate.update("DELETE FROM notification_preference");
        jdbcTemplate.update("DELETE FROM outbox_message");
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void fansOutToConsentedChannelsWithEncryptedPayloadsAndDeterministicReplay() {
        UUID userId = register("mobile-delivery@example.test");
        contacts.save(userId, "+27821234567", true, true);
        preferences.set(userId, NotificationCategory.SHIPMENT_UPDATE, null, true, true);
        UUID eventId = UUID.randomUUID();
        var request = new MobileNotificationRequests.UserMobileRequest(
                "TEST_SHIPMENT_READY",
                eventId,
                userId,
                "SHIPMENT_UPDATE",
                NotificationTemplates.CAPACITY_MATCH_FOUND,
                NotificationTemplates.CAPACITY_MATCH_FOUND_VERSION,
                Map.of());

        var first = notifications.requestUser(request);
        var replay = notifications.requestUser(request);

        assertThat(first).hasSize(2).containsExactlyElementsOf(replay);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mobile_notification", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_message", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT status FROM mobile_notification", String.class))
                .containsOnly("PENDING");
        assertThat(jdbcTemplate.queryForList("SELECT protected_recipient FROM mobile_notification", String.class))
                .allSatisfy(value -> assertThat(value).startsWith("v1:").doesNotContain("27821234567"));
        assertThat(jdbcTemplate.queryForList("SELECT payload::text FROM outbox_message", String.class))
                .allSatisfy(value -> assertThat(value)
                        .doesNotContain("27821234567", "Transport matches are ready")
                        .contains("notificationId"));

        assertThat(outboxWorker.pollOnce()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT status FROM mobile_notification", String.class))
                .containsOnly("SENT");
        assertThat(jdbcTemplate.queryForList("SELECT status FROM mobile_delivery_attempt", String.class))
                .containsOnly("ACCEPTED");
        assertThat(capture.capturedMessages()).hasSize(2).allSatisfy(message -> {
            assertThat(message.recipientPhone()).isEqualTo("+27821234567");
            assertThat(message.body()).isEqualTo("Transport matches are ready. Sign in to TradeMesh to review them.");
        });
    }

    @Test
    void recordsSuppressionWithoutRecipientOrOutboxPayload() {
        UUID userId = register("mobile-suppressed@example.test");
        var request = new MobileNotificationRequests.UserMobileRequest(
                "TEST_OPTIONAL_EVENT",
                UUID.randomUUID(),
                userId,
                "SHIPMENT_UPDATE",
                NotificationTemplates.ESCROW_RELEASED,
                NotificationTemplates.ESCROW_RELEASED_VERSION,
                Map.of());

        var ids = notifications.requestUser(request);

        assertThat(ids).hasSize(2);
        assertThat(jdbcTemplate.queryForList("SELECT status FROM mobile_notification", String.class))
                .containsOnly("SUPPRESSED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM mobile_notification WHERE protected_recipient IS NOT NULL",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_message", Integer.class))
                .isZero();

        contacts.save(userId, "+27821234567", true, true);
        preferences.set(userId, NotificationCategory.SHIPMENT_UPDATE, null, true, true);
        assertThat(notifications.requestUser(request)).containsExactlyElementsOf(ids);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mobile_notification", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_message", Integer.class))
                .isZero();
    }

    private UUID register(String email) {
        return authService
                .register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER)
                .userId();
    }
}
