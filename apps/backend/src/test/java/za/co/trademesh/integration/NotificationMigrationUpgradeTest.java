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

class NotificationMigrationUpgradeTest extends PostgresIntegrationTest {

    @Test
    void upgradesADatabaseThatAlreadyHasDemandAggregation() throws Exception {
        String schema = "notification_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway beforeNotification = flyway(schema, MigrationVersion.fromVersion("20260902084500"));
            assertThat(beforeNotification.migrate().migrationsExecuted).isEqualTo(10);
            assertThat(tableExists(schema, "email_notification")).isFalse();

            Flyway notificationMigration = flyway(schema, MigrationVersion.fromVersion("20260902100000"));
            assertThat(notificationMigration.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(tableExists(schema, "notification_preference")).isTrue();
            assertThat(tableExists(schema, "email_notification")).isTrue();
            assertThat(tableExists(schema, "email_delivery_attempt")).isTrue();
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
        if (!schema.matches("notification_upgrade_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Refusing to drop an unexpected schema");
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
