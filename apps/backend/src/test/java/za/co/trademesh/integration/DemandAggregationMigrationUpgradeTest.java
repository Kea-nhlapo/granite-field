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

class DemandAggregationMigrationUpgradeTest extends PostgresIntegrationTest {

    @Test
    void upgradesADatabaseThatAlreadyHasDocumentComparisons() throws Exception {
        String schema = "aggregation_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway beforeAggregation = flyway(schema, MigrationVersion.fromVersion("20260902073000"));
            assertThat(beforeAggregation.migrate().migrationsExecuted).isEqualTo(9);
            assertThat(tableExists(schema, "demand_group_suggestion")).isFalse();

            Flyway aggregationMigration = flyway(schema, MigrationVersion.fromVersion("20260902084500"));
            assertThat(aggregationMigration.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(tableExists(schema, "demand_group_suggestion")).isTrue();
            assertThat(tableExists(schema, "demand_group_order_evaluation")).isTrue();
            assertThat(tableExists(schema, "demand_group_constraint_result")).isTrue();
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
        if (!schema.matches("aggregation_upgrade_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Refusing to drop an unexpected schema");
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
