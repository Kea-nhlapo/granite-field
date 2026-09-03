package za.co.trademesh.modules.notification.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.notification.domain.EmailDeliveryAttempt;
import za.co.trademesh.modules.notification.domain.EmailDeliveryAttemptStatus;
import za.co.trademesh.modules.notification.domain.EmailNotification;
import za.co.trademesh.modules.notification.domain.EmailNotificationStatus;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.NotificationCategory;
import za.co.trademesh.modules.notification.domain.NotificationContactPoint;
import za.co.trademesh.modules.notification.domain.NotificationPreference;
import za.co.trademesh.modules.notification.domain.NotificationRepository;

@Repository
class JdbcNotificationRepository implements NotificationRepository {

    private static final String NOTIFICATION_COLUMNS = """
        id, idempotency_key, request_fingerprint, recipient_email, recipient_user_id,
        category, template_key, template_version, status, created_at, sent_at, failed_at
        """;

    private final JdbcTemplate jdbcTemplate;
    private final SensitiveNotificationDataProtector dataProtector;

    JdbcNotificationRepository(JdbcTemplate jdbcTemplate, SensitiveNotificationDataProtector dataProtector) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataProtector = dataProtector;
    }

    @Override
    public boolean saveNotification(EmailNotification notification) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO email_notification (
                id, idempotency_key, request_fingerprint, recipient_email, recipient_user_id,
                category, template_key, template_version, status, created_at, sent_at, failed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """,
                notification.id(),
                notification.idempotencyKey(),
                notification.requestFingerprint(),
                notification.recipientEmail(),
                notification.recipientUserId(),
                notification.category().name(),
                notification.templateKey(),
                notification.templateVersion(),
                notification.status().name(),
                time(notification.createdAt()),
                nullableTime(notification.sentAt()),
                nullableTime(notification.failedAt()));
        if (written != 1) {
            return false;
        }
        notification.templateData().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> jdbcTemplate.update(
                        """
                    INSERT INTO email_notification_template_data (notification_id, data_key, data_value)
                    VALUES (?, ?, ?)
                    """, notification.id(), entry.getKey(), dataProtector.protect(entry.getValue())));
        return true;
    }

    @Override
    public Optional<EmailNotification> findNotification(UUID notificationId) {
        return one("SELECT " + NOTIFICATION_COLUMNS + " FROM email_notification WHERE id = ?", notificationId);
    }

    @Override
    public Optional<EmailNotification> findByIdempotencyKey(String idempotencyKey) {
        return one(
                "SELECT " + NOTIFICATION_COLUMNS + " FROM email_notification WHERE idempotency_key = ?",
                idempotencyKey);
    }

    @Override
    public boolean saveAttempt(EmailDeliveryAttempt attempt) {
        return jdbcTemplate.update(
                        """
            INSERT INTO email_delivery_attempt (
                id, notification_id, outbox_message_id, attempt_number, provider_key,
                status, provider_message_id, failure_code, failure_message,
                started_at, completed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (notification_id, outbox_message_id, attempt_number) DO NOTHING
            """,
                        attempt.id(),
                        attempt.notificationId(),
                        attempt.outboxMessageId(),
                        attempt.attemptNumber(),
                        attempt.providerKey(),
                        attempt.status().name(),
                        attempt.providerMessageId(),
                        attempt.failureCode(),
                        attempt.failureMessage(),
                        time(attempt.startedAt()),
                        nullableTime(attempt.completedAt()))
                == 1;
    }

    @Override
    public Optional<EmailDeliveryAttempt> findAttempt(UUID notificationId, UUID outboxMessageId, int attemptNumber) {
        return jdbcTemplate.query("""
                SELECT id, notification_id, outbox_message_id, attempt_number, provider_key,
                       status, provider_message_id, failure_code, failure_message,
                       started_at, completed_at
                  FROM email_delivery_attempt
                 WHERE notification_id = ? AND outbox_message_id = ? AND attempt_number = ?
                """, this::mapAttempt, notificationId, outboxMessageId, attemptNumber).stream()
                .findFirst();
    }

    @Override
    public void markSent(UUID notificationId, UUID attemptId, String providerMessageId, Instant completedAt) {
        int attemptUpdated =
                jdbcTemplate.update("""
            UPDATE email_delivery_attempt
               SET status = 'SENT', provider_message_id = ?, completed_at = ?
             WHERE id = ? AND notification_id = ? AND status = 'STARTED'
            """, limit(providerMessageId, 200), time(completedAt), attemptId, notificationId);
        int notificationUpdated = jdbcTemplate.update("""
            UPDATE email_notification
               SET status = 'SENT', sent_at = ?, failed_at = NULL
             WHERE id = ? AND status = 'PENDING'
            """, time(completedAt), notificationId);
        if (attemptUpdated != 1 || notificationUpdated != 1) {
            throw new IllegalStateException("Email delivery completion conflict");
        }
    }

    @Override
    public void markFailed(
            UUID notificationId,
            UUID attemptId,
            String failureCode,
            String failureMessage,
            boolean finalFailure,
            Instant completedAt) {
        int attemptUpdated = jdbcTemplate.update(
                """
            UPDATE email_delivery_attempt
               SET status = 'FAILED', failure_code = ?, failure_message = ?, completed_at = ?
             WHERE id = ? AND notification_id = ? AND status = 'STARTED'
            """, limit(failureCode, 64), limit(failureMessage, 500), time(completedAt), attemptId, notificationId);
        if (attemptUpdated != 1) {
            throw new IllegalStateException("Email delivery failure conflict");
        }
        if (finalFailure) {
            int notificationUpdated = jdbcTemplate.update("""
                UPDATE email_notification
                   SET status = 'FAILED', failed_at = ?
                 WHERE id = ? AND status = 'PENDING'
                """, time(completedAt), notificationId);
            if (notificationUpdated != 1) {
                throw new IllegalStateException("Email notification failure conflict");
            }
        }
    }

    @Override
    public boolean emailEnabled(UUID userId, NotificationCategory category) {
        Boolean enabled = jdbcTemplate.queryForObject("""
            SELECT COALESCE(
                (SELECT email_enabled FROM notification_preference WHERE user_id = ? AND category = ?),
                TRUE)
            """, Boolean.class, userId, category.name());
        return !Boolean.FALSE.equals(enabled);
    }

    @Override
    public boolean mobileEnabled(UUID userId, NotificationCategory category, MobileChannel channel) {
        String column =
                switch (channel) {
                    case SMS -> "sms_enabled";
                    case WHATSAPP -> "whatsapp_enabled";
                };
        Boolean enabled = jdbcTemplate.queryForObject("""
            SELECT COALESCE(
                (SELECT %s FROM notification_preference WHERE user_id = ? AND category = ?),
                FALSE)
            """.formatted(column), Boolean.class, userId, category.name());
        return Boolean.TRUE.equals(enabled);
    }

    @Override
    public NotificationPreference savePreference(NotificationPreference preference) {
        jdbcTemplate.update(
                """
            INSERT INTO notification_preference (
                user_id, category, email_enabled, sms_enabled, whatsapp_enabled, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, category) DO UPDATE
                SET email_enabled = EXCLUDED.email_enabled,
                    sms_enabled = EXCLUDED.sms_enabled,
                    whatsapp_enabled = EXCLUDED.whatsapp_enabled,
                    updated_at = EXCLUDED.updated_at
            """,
                preference.userId(),
                preference.category().name(),
                preference.emailEnabled(),
                preference.smsEnabled(),
                preference.whatsappEnabled(),
                time(preference.updatedAt()));
        return preference;
    }

    @Override
    public List<NotificationPreference> findPreferences(UUID userId) {
        return jdbcTemplate.query(
                """
            SELECT user_id, category, email_enabled, sms_enabled, whatsapp_enabled, updated_at
              FROM notification_preference
             WHERE user_id = ?
             ORDER BY category
            """,
                (resultSet, rowNumber) -> new NotificationPreference(
                        resultSet.getObject("user_id", UUID.class),
                        NotificationCategory.valueOf(resultSet.getString("category")),
                        resultSet.getBoolean("email_enabled"),
                        resultSet.getBoolean("sms_enabled"),
                        resultSet.getBoolean("whatsapp_enabled"),
                        instant(resultSet, "updated_at")),
                userId);
    }

    @Override
    public Optional<NotificationContactPoint> findContact(UUID userId) {
        return jdbcTemplate
                .query(
                        """
            SELECT user_id, protected_phone, phone_fingerprint, phone_last_four,
                   sms_consented_at, whatsapp_consented_at, created_at, updated_at
              FROM notification_contact_point
             WHERE user_id = ?
            """,
                        (resultSet, rowNumber) -> new NotificationContactPoint(
                                resultSet.getObject("user_id", UUID.class),
                                dataProtector.unprotect(resultSet.getString("protected_phone")),
                                resultSet.getString("phone_fingerprint"),
                                resultSet.getString("phone_last_four"),
                                nullableInstant(resultSet, "sms_consented_at"),
                                nullableInstant(resultSet, "whatsapp_consented_at"),
                                instant(resultSet, "created_at"),
                                instant(resultSet, "updated_at")),
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public NotificationContactPoint saveContact(NotificationContactPoint contact) {
        jdbcTemplate.update(
                """
            INSERT INTO notification_contact_point (
                user_id, protected_phone, phone_fingerprint, phone_last_four,
                sms_consented_at, whatsapp_consented_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE
                SET protected_phone = EXCLUDED.protected_phone,
                    phone_fingerprint = EXCLUDED.phone_fingerprint,
                    phone_last_four = EXCLUDED.phone_last_four,
                    sms_consented_at = EXCLUDED.sms_consented_at,
                    whatsapp_consented_at = EXCLUDED.whatsapp_consented_at,
                    updated_at = EXCLUDED.updated_at
            """,
                contact.userId(),
                dataProtector.protect(contact.phoneNumber()),
                contact.phoneFingerprint(),
                contact.phoneLastFour(),
                nullableTime(contact.smsConsentedAt()),
                nullableTime(contact.whatsappConsentedAt()),
                time(contact.createdAt()),
                time(contact.updatedAt()));
        return findContact(contact.userId())
                .orElseThrow(() -> new IllegalStateException("Notification contact persistence failed"));
    }

    @Override
    public void deleteContact(UUID userId) {
        jdbcTemplate.update("DELETE FROM notification_contact_point WHERE user_id = ?", userId);
    }

    @Override
    public void disableMobilePreferences(UUID userId, Instant updatedAt) {
        jdbcTemplate.update("""
            UPDATE notification_preference
               SET sms_enabled = FALSE, whatsapp_enabled = FALSE, updated_at = ?
             WHERE user_id = ? AND (sms_enabled OR whatsapp_enabled)
            """, time(updatedAt), userId);
    }

    private Optional<EmailNotification> one(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapNotification, parameters).stream()
                .findFirst();
    }

    private EmailNotification mapNotification(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID notificationId = resultSet.getObject("id", UUID.class);
        Map<String, String> templateData = new LinkedHashMap<>();
        List<Map.Entry<String, String>> dataEntries = jdbcTemplate.query(
                """
            SELECT data_key, data_value
              FROM email_notification_template_data
             WHERE notification_id = ?
             ORDER BY data_key
            """,
                (dataSet, dataNumber) -> Map.entry(dataSet.getString("data_key"), dataSet.getString("data_value")),
                notificationId);
        dataEntries.forEach(entry -> templateData.put(entry.getKey(), dataProtector.unprotect(entry.getValue())));
        return new EmailNotification(
                notificationId,
                resultSet.getString("idempotency_key"),
                resultSet.getString("request_fingerprint").strip(),
                resultSet.getString("recipient_email"),
                resultSet.getObject("recipient_user_id", UUID.class),
                NotificationCategory.valueOf(resultSet.getString("category")),
                resultSet.getString("template_key"),
                resultSet.getInt("template_version"),
                templateData,
                EmailNotificationStatus.valueOf(resultSet.getString("status")),
                instant(resultSet, "created_at"),
                nullableInstant(resultSet, "sent_at"),
                nullableInstant(resultSet, "failed_at"));
    }

    private EmailDeliveryAttempt mapAttempt(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EmailDeliveryAttempt(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("notification_id", UUID.class),
                resultSet.getObject("outbox_message_id", UUID.class),
                resultSet.getInt("attempt_number"),
                resultSet.getString("provider_key"),
                EmailDeliveryAttemptStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("provider_message_id"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                instant(resultSet, "started_at"),
                nullableInstant(resultSet, "completed_at"));
    }

    private static String limit(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static OffsetDateTime nullableTime(Instant value) {
        return value == null ? null : time(value);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
