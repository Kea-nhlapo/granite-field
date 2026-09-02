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

class HandoverDiscrepancyMigrationUpgradeTest extends PostgresIntegrationTest {

    @Test
    void addsQuantityEvidenceAndResolutionAfterEscrow() throws Exception {
        String schema =
                "handover_discrepancy_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway beforeDiscrepancy = flyway(schema, MigrationVersion.fromVersion("20260903160000"));
            assertThat(beforeDiscrepancy.migrate().migrationsExecuted).isEqualTo(25);
            assertThat(tableExists(schema, "handover_delivery_resolution")).isFalse();
            assertThat(columnExists(schema, "handover_challenge", "expected_quantity"))
                    .isFalse();

            Flyway discrepancyMigration = flyway(schema, MigrationVersion.fromVersion("20260903173000"));
            assertThat(discrepancyMigration.migrate().migrationsExecuted).isOne();
            assertThat(tableExists(schema, "handover_delivery_resolution")).isTrue();
            assertThat(columnExists(schema, "handover_challenge", "expected_quantity"))
                    .isTrue();
            assertThat(columnExists(schema, "handover_confirmation", "captured_quantity"))
                    .isTrue();
            assertThat(columnExists(schema, "handover_confirmation", "photo_url"))
                    .isTrue();
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

    private static boolean columnExists(String schema, String table, String column) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
                var statement = connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ?)")) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static void dropSchema(String schema) throws Exception {
        if (!schema.matches("handover_discrepancy_upgrade_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Refusing to drop an unexpected schema");
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
