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
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.MobileDeliveryAttempt;
import za.co.trademesh.modules.notification.domain.MobileDeliveryAttemptStatus;
import za.co.trademesh.modules.notification.domain.MobileNotification;
import za.co.trademesh.modules.notification.domain.MobileNotificationRepository;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;
import za.co.trademesh.modules.notification.domain.MobileStatusObservation;
import za.co.trademesh.modules.notification.domain.NotificationCategory;

@Repository
class JdbcMobileNotificationRepository implements MobileNotificationRepository {

    private static final String NOTIFICATION_COLUMNS = """
        id, idempotency_key, request_fingerprint, recipient_user_id, channel, category,
        template_key, template_version, protected_recipient, status, provider_key,
        provider_message_id, created_at, submitted_at, sent_at, delivered_at,
        read_at, failed_at, updated_at
        """;

    private final JdbcTemplate jdbcTemplate;
    private final SensitiveNotificationDataProtector dataProtector;

    JdbcMobileNotificationRepository(JdbcTemplate jdbcTemplate, SensitiveNotificationDataProtector dataProtector) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataProtector = dataProtector;
    }

    @Override
    public boolean saveNotification(MobileNotification notification) {
        String recipient =
                notification.recipientPhone() == null ? null : dataProtector.protect(notification.recipientPhone());
        String lastFour = notification.recipientPhone() == null
                ? null
                : notification
                        .recipientPhone()
                        .substring(notification.recipientPhone().length() - 4);
        int written = jdbcTemplate.update(
                """
            INSERT INTO mobile_notification (
                id, idempotency_key, request_fingerprint, recipient_user_id, channel, category,
                template_key, template_version, protected_recipient, recipient_last_four,
                status, provider_key, provider_message_id, created_at, submitted_at, sent_at,
                delivered_at, read_at, failed_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """,
                notification.id(),
                notification.idempotencyKey(),
                notification.requestFingerprint(),
                notification.recipientUserId(),
                notification.channel().name(),
                notification.category().name(),
                notification.templateKey(),
                notification.templateVersion(),
                recipient,
                lastFour,
                notification.status().name(),
                notification.providerKey(),
                notification.providerMessageId(),
                time(notification.createdAt()),
                nullableTime(notification.submittedAt()),
                nullableTime(notification.sentAt()),
                nullableTime(notification.deliveredAt()),
                nullableTime(notification.readAt()),
                nullableTime(notification.failedAt()),
                time(notification.updatedAt()));
        if (written != 1) {
            return false;
        }
        notification.templateData().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> jdbcTemplate.update(
                        """
                    INSERT INTO mobile_notification_template_data (
                        notification_id, data_key, protected_data_value
                    ) VALUES (?, ?, ?)
                    """, notification.id(), entry.getKey(), dataProtector.protect(entry.getValue())));
        return true;
    }

    @Override
    public Optional<MobileNotification> findNotification(UUID notificationId) {
        return one("SELECT " + NOTIFICATION_COLUMNS + " FROM mobile_notification WHERE id = ?", notificationId);
    }

    @Override
    public Optional<MobileNotification> findNotificationForUpdate(UUID notificationId) {
        return one(
                "SELECT " + NOTIFICATION_COLUMNS + " FROM mobile_notification WHERE id = ? FOR UPDATE", notificationId);
    }

    @Override
    public Optional<MobileNotification> findByIdempotencyKey(String idempotencyKey) {
        return one(
                "SELECT " + NOTIFICATION_COLUMNS + " FROM mobile_notification WHERE idempotency_key = ?",
                idempotencyKey);
    }

    @Override
    public Optional<MobileNotification> findByProviderMessageId(String providerKey, String providerMessageId) {
        return one(
                "SELECT " + NOTIFICATION_COLUMNS
                        + " FROM mobile_notification WHERE provider_key = ? AND provider_message_id = ?",
                providerKey,
                providerMessageId);
    }

    @Override
    public boolean saveAttempt(MobileDeliveryAttempt attempt) {
        return jdbcTemplate.update(
                        """
            INSERT INTO mobile_delivery_attempt (
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
    public Optional<MobileDeliveryAttempt> findAttempt(UUID notificationId, UUID outboxMessageId, int attemptNumber) {
        return jdbcTemplate.query("""
                SELECT id, notification_id, outbox_message_id, attempt_number, provider_key,
                       status, provider_message_id, failure_code, failure_message,
                       started_at, completed_at
                  FROM mobile_delivery_attempt
                 WHERE notification_id = ? AND outbox_message_id = ? AND attempt_number = ?
                """, this::mapAttempt, notificationId, outboxMessageId, attemptNumber).stream()
                .findFirst();
    }

    @Override
    public Optional<MobileDeliveryAttempt> findLatestStartedAttempt(UUID notificationId) {
        return jdbcTemplate.query("""
                SELECT id, notification_id, outbox_message_id, attempt_number, provider_key,
                       status, provider_message_id, failure_code, failure_message,
                       started_at, completed_at
                  FROM mobile_delivery_attempt
                 WHERE notification_id = ? AND status = 'STARTED'
                 ORDER BY started_at DESC, id DESC
                 LIMIT 1
                """, this::mapAttempt, notificationId).stream()
                .findFirst();
    }

    @Override
    public boolean markSubmitting(UUID notificationId, Instant updatedAt) {
        return jdbcTemplate.update("""
            UPDATE mobile_notification
               SET status = 'SUBMITTING', updated_at = ?
             WHERE id = ? AND status = 'PENDING'
            """, time(updatedAt), notificationId) == 1;
    }

    @Override
    public void markSubmitted(
            UUID notificationId,
            UUID attemptId,
            String providerKey,
            String providerMessageId,
            MobileNotificationStatus status,
            Instant completedAt) {
        if (status != MobileNotificationStatus.ACCEPTED
                && status != MobileNotificationStatus.QUEUED
                && status != MobileNotificationStatus.SENT) {
            throw new IllegalArgumentException("Invalid submitted mobile notification status");
        }
        int attemptUpdated =
                jdbcTemplate.update("""
            UPDATE mobile_delivery_attempt
               SET status = 'ACCEPTED', provider_message_id = ?, failure_code = NULL,
                   failure_message = NULL, completed_at = ?
             WHERE id = ? AND notification_id = ? AND status IN ('STARTED', 'UNKNOWN')
            """, limit(providerMessageId, 200), time(completedAt), attemptId, notificationId);
        int notificationUpdated = jdbcTemplate.update(
                """
            UPDATE mobile_notification
               SET status = ?, provider_key = ?, provider_message_id = ?,
                   submitted_at = COALESCE(submitted_at, ?),
                   sent_at = CASE WHEN ? = 'SENT' THEN COALESCE(sent_at, ?) ELSE sent_at END,
                   updated_at = ?
             WHERE id = ? AND status IN ('SUBMITTING', 'SUBMISSION_UNKNOWN')
            """,
                status.name(),
                limit(providerKey, 100),
                limit(providerMessageId, 200),
                time(completedAt),
                status.name(),
                time(completedAt),
                time(completedAt),
                notificationId);
        if (attemptUpdated != 1) {
            throw new IllegalStateException("Mobile delivery completion conflict");
        }
        if (notificationUpdated == 0) {
            MobileNotification current = findNotificationForUpdate(notificationId)
                    .orElseThrow(() -> new IllegalStateException("Mobile notification disappeared"));
            boolean providerMatches =
                    providerKey.equals(current.providerKey()) && providerMessageId.equals(current.providerMessageId());
            if (!providerMatches
                    || current.status() == MobileNotificationStatus.PENDING
                    || current.status() == MobileNotificationStatus.SUBMITTING
                    || current.status() == MobileNotificationStatus.SUBMISSION_UNKNOWN
                    || current.status() == MobileNotificationStatus.SUPPRESSED) {
                throw new IllegalStateException("Mobile delivery completion conflict");
            }
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
            UPDATE mobile_delivery_attempt
               SET status = 'FAILED', failure_code = ?, failure_message = ?, completed_at = ?
             WHERE id = ? AND notification_id = ? AND status = 'STARTED'
            """, limit(failureCode, 64), limit(failureMessage, 500), time(completedAt), attemptId, notificationId);
        int notificationUpdated = jdbcTemplate.update(
                """
            UPDATE mobile_notification
               SET status = ?, failed_at = CASE WHEN ? THEN ? ELSE NULL END, updated_at = ?
             WHERE id = ? AND status = 'SUBMITTING'
            """,
                finalFailure ? MobileNotificationStatus.FAILED.name() : MobileNotificationStatus.PENDING.name(),
                finalFailure,
                time(completedAt),
                time(completedAt),
                notificationId);
        if (attemptUpdated != 1 || notificationUpdated != 1) {
            throw new IllegalStateException("Mobile delivery failure conflict");
        }
    }

    @Override
    public void markSubmissionUnknown(
            UUID notificationId,
            UUID attemptId,
            String providerKey,
            String failureCode,
            String failureMessage,
            Instant completedAt) {
        int attemptUpdated = jdbcTemplate.update(
                """
            UPDATE mobile_delivery_attempt
               SET status = 'UNKNOWN', failure_code = ?, failure_message = ?, completed_at = ?
             WHERE id = ? AND notification_id = ? AND status = 'STARTED'
            """, limit(failureCode, 64), limit(failureMessage, 500), time(completedAt), attemptId, notificationId);
        int notificationUpdated =
                jdbcTemplate.update("""
            UPDATE mobile_notification
               SET status = 'SUBMISSION_UNKNOWN', provider_key = ?, submitted_at = ?, updated_at = ?
             WHERE id = ? AND status = 'SUBMITTING'
            """, limit(providerKey, 100), time(completedAt), time(completedAt), notificationId);
        if (attemptUpdated != 1 || notificationUpdated != 1) {
            throw new IllegalStateException("Mobile delivery unknown-state conflict");
        }
    }

    @Override
    public boolean saveObservation(MobileStatusObservation observation) {
        return jdbcTemplate.update(
                        """
            INSERT INTO mobile_status_observation (
                id, notification_id, callback_fingerprint, provider_key, provider_message_id,
                provider_status, observed_at, received_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (callback_fingerprint) DO NOTHING
            """,
                        observation.id(),
                        observation.notificationId(),
                        observation.callbackFingerprint(),
                        observation.providerKey(),
                        limit(observation.providerMessageId(), 200),
                        limit(observation.providerStatus(), 100),
                        nullableTime(observation.observedAt()),
                        time(observation.receivedAt()))
                == 1;
    }

    @Override
    public void updateStatus(
            UUID notificationId,
            String providerKey,
            String providerMessageId,
            MobileNotificationStatus status,
            Instant observedAt,
            Instant updatedAt) {
        Instant eventTime = observedAt == null ? updatedAt : observedAt;
        boolean sent = status == MobileNotificationStatus.SENT
                || status == MobileNotificationStatus.DELIVERED
                || status == MobileNotificationStatus.READ;
        boolean delivered = status == MobileNotificationStatus.DELIVERED || status == MobileNotificationStatus.READ;
        boolean read = status == MobileNotificationStatus.READ;
        boolean failed = status.finalFailure();
        int updated = jdbcTemplate.update(
                """
            UPDATE mobile_notification
               SET status = ?,
                   provider_key = COALESCE(provider_key, ?),
                   provider_message_id = COALESCE(provider_message_id, ?),
                   submitted_at = COALESCE(submitted_at, ?),
                   sent_at = CASE WHEN ? THEN COALESCE(sent_at, ?) ELSE sent_at END,
                   delivered_at = CASE WHEN ? THEN COALESCE(delivered_at, ?) ELSE delivered_at END,
                   read_at = CASE WHEN ? THEN COALESCE(read_at, ?) ELSE read_at END,
                   failed_at = CASE WHEN ? THEN COALESCE(failed_at, ?) ELSE failed_at END,
                   updated_at = ?
             WHERE id = ?
            """,
                status.name(),
                limit(providerKey, 100),
                limit(providerMessageId, 200),
                time(eventTime),
                sent,
                time(eventTime),
                delivered,
                time(eventTime),
                read,
                time(eventTime),
                failed,
                time(eventTime),
                time(updatedAt),
                notificationId);
        if (updated != 1) {
            throw new IllegalStateException("Mobile notification status update conflict");
        }
    }

    private Optional<MobileNotification> one(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapNotification, parameters).stream()
                .findFirst();
    }

    private MobileNotification mapNotification(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID notificationId = resultSet.getObject("id", UUID.class);
        Map<String, String> templateData = new LinkedHashMap<>();
        List<Map.Entry<String, String>> entries = jdbcTemplate.query(
                """
            SELECT data_key, protected_data_value
              FROM mobile_notification_template_data
             WHERE notification_id = ?
             ORDER BY data_key
            """,
                (dataSet, dataNumber) ->
                        Map.entry(dataSet.getString("data_key"), dataSet.getString("protected_data_value")),
                notificationId);
        entries.forEach(entry -> templateData.put(entry.getKey(), dataProtector.unprotect(entry.getValue())));
        String protectedRecipient = resultSet.getString("protected_recipient");
        return new MobileNotification(
                notificationId,
                resultSet.getString("idempotency_key"),
                resultSet.getString("request_fingerprint"),
                resultSet.getObject("recipient_user_id", UUID.class),
                MobileChannel.valueOf(resultSet.getString("channel")),
                NotificationCategory.valueOf(resultSet.getString("category")),
                resultSet.getString("template_key"),
                resultSet.getInt("template_version"),
                protectedRecipient == null ? null : dataProtector.unprotect(protectedRecipient),
                templateData,
                MobileNotificationStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("provider_key"),
                resultSet.getString("provider_message_id"),
                instant(resultSet, "created_at"),
                nullableInstant(resultSet, "submitted_at"),
                nullableInstant(resultSet, "sent_at"),
                nullableInstant(resultSet, "delivered_at"),
                nullableInstant(resultSet, "read_at"),
                nullableInstant(resultSet, "failed_at"),
                instant(resultSet, "updated_at"));
    }

    private MobileDeliveryAttempt mapAttempt(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MobileDeliveryAttempt(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("notification_id", UUID.class),
                resultSet.getObject("outbox_message_id", UUID.class),
                resultSet.getInt("attempt_number"),
                resultSet.getString("provider_key"),
                MobileDeliveryAttemptStatus.valueOf(resultSet.getString("status")),
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
