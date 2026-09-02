package za.co.trademesh.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import za.co.trademesh.shared.events.outbox.OutboxWorker;
import za.co.trademesh.support.PostgresIntegrationTest;

@Import(EmailDeliveryRetryIntegrationTest.FailingProviderConfiguration.class)
@TestPropertySource(
        properties = {"trademesh.outbox.enabled=false", "trademesh.notifications.email.max-delivery-attempts=2"})
class EmailDeliveryRetryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private NotificationRequests notifications;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailingEmailProvider provider;

    @BeforeEach
    @AfterEach
    void cleanState() {
        provider.reset();
        jdbcTemplate.update("DELETE FROM email_delivery_attempt");
        jdbcTemplate.update("DELETE FROM email_notification_template_data");
        jdbcTemplate.update("DELETE FROM email_notification");
        jdbcTemplate.update("DELETE FROM notification_preference");
        jdbcTemplate.update("DELETE FROM outbox_message");
    }

    @Test
    void recordsEachFailureAndStopsAtTheConfiguredAttemptLimit() {
        UUID notificationId = notifications.requestEmail(new NotificationRequests.EmailRequest(
                "bounded-retry:" + UUID.randomUUID(),
                "recipient@example.com",
                null,
                "PROCUREMENT_UPDATE",
                NotificationTemplates.PROCUREMENT_ORDER_CONFIRMED,
                NotificationTemplates.PROCUREMENT_ORDER_CONFIRMED_VERSION,
                Map.of(),
                true));

        assertThat(outboxWorker.pollOnce()).isOne();
        assertThat(status(notificationId)).isEqualTo("PENDING");
        assertThat(attemptCount(notificationId)).isOne();
        assertThat(outboxStatus()).isEqualTo("PENDING");

        jdbcTemplate.update(
                "UPDATE outbox_message SET available_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE status = 'PENDING'");
        assertThat(outboxWorker.pollOnce()).isOne();

        assertThat(status(notificationId)).isEqualTo("FAILED");
        assertThat(attemptCount(notificationId)).isEqualTo(2);
        assertThat(provider.calls()).isEqualTo(2);
        assertThat(outboxStatus()).isEqualTo("DONE");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT failure_code FROM email_delivery_attempt WHERE notification_id = ? ORDER BY attempt_number",
                        String.class,
                        notificationId))
                .containsExactly("DEMO_PROVIDER_DOWN", "DEMO_PROVIDER_DOWN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT failure_message FROM email_delivery_attempt WHERE notification_id = ?",
                        String.class,
                        notificationId))
                .allSatisfy(message ->
                        assertThat(message).doesNotContain("recipient@example.com", "Your order status changed"));
    }

    private String status(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM email_notification WHERE id = ?", String.class, notificationId);
    }

    private int attemptCount(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_delivery_attempt WHERE notification_id = ?", Integer.class, notificationId);
    }

    private String outboxStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_message WHERE type = 'notification.email-delivery-requested'", String.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingProviderConfiguration {

        @Bean
        @Primary
        FailingEmailProvider failingEmailProvider() {
            return new FailingEmailProvider();
        }
    }

    static final class FailingEmailProvider implements EmailDeliveryProvider {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String providerKey() {
            return "failing-test-provider";
        }

        @Override
        public DeliveryResult deliver(EmailMessage message) throws EmailProviderException {
            calls.incrementAndGet();
            throw new EmailProviderException(
                    "DEMO_PROVIDER_DOWN", "The email provider is temporarily unavailable.", true);
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }
}
