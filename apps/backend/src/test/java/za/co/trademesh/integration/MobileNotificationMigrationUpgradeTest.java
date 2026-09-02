package za.co.trademesh.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import za.co.trademesh.support.PostgresIntegrationTest;

class MobileNotificationMigrationUpgradeTest extends PostgresIntegrationTest {

    @Test
    void addsConsentedDurableMobileDeliveryAfterTrustScores() throws Exception {
        String schema =
                "mobile_notification_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway beforeMobileNotifications = flyway(schema, MigrationVersion.fromVersion("20260903190000"));
            assertThat(beforeMobileNotifications.migrate().migrationsExecuted).isEqualTo(27);
            assertThat(tableExists(schema, "mobile_notification")).isFalse();

            UUID userId = UUID.randomUUID();
            try (Connection connection = POSTGRES.createConnection("");
                    var statement = connection.prepareStatement("""
                        INSERT INTO %s.access_user_account (id, email, password_hash, enabled, created_at)
                        VALUES (?, ?, ?, TRUE, NOW())
                        """.formatted(schema))) {
                statement.setObject(1, userId);
                statement.setString(2, "mobile-upgrade@example.test");
                statement.setString(3, "{noop}not-used");
                statement.executeUpdate();
            }
            try (Connection connection = POSTGRES.createConnection("");
                    var statement = connection.prepareStatement("""
                        INSERT INTO %s.notification_preference (user_id, category, email_enabled, updated_at)
                        VALUES (?, 'SHIPMENT_UPDATE', FALSE, NOW())
                        """.formatted(schema))) {
                statement.setObject(1, userId);
                statement.executeUpdate();
            }

            Flyway mobileNotificationMigration = flyway(schema, MigrationVersion.fromVersion("20260903200000"));
            assertThat(mobileNotificationMigration.migrate().migrationsExecuted).isOne();
            assertThat(tableExists(schema, "notification_contact_point")).isTrue();
            assertThat(tableExists(schema, "mobile_notification")).isTrue();
            assertThat(tableExists(schema, "mobile_notification_template_data")).isTrue();
            assertThat(tableExists(schema, "mobile_delivery_attempt")).isTrue();
            assertThat(tableExists(schema, "mobile_status_observation")).isTrue();

            try (Connection connection = POSTGRES.createConnection("");
                    var statement = connection.prepareStatement("""
                        SELECT email_enabled, sms_enabled, whatsapp_enabled
                          FROM %s.notification_preference
                         WHERE user_id = ? AND category = 'SHIPMENT_UPDATE'
                        """.formatted(schema))) {
                statement.setObject(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean("email_enabled")).isFalse();
                    assertThat(result.getBoolean("sms_enabled")).isFalse();
                    assertThat(result.getBoolean("whatsapp_enabled")).isFalse();
                }
            }
        } finally {
            dropSchema(schema);
        }
    }

    private static Flyway flyway(String schema, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(target)
                .load();
    }

    private static boolean tableExists(String schema, String table) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
                var statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, schema + "." + table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1) != null;
            }
        }
    }

    private static void dropSchema(String schema) throws Exception {
        if (!schema.matches("mobile_notification_upgrade_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Refusing to drop an unexpected schema");
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
