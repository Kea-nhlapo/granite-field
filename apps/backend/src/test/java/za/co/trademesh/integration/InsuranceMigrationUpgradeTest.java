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

class InsuranceMigrationUpgradeTest extends PostgresIntegrationTest {

    @Test
    void addsPurposeScopedInsuranceCasesAndAppendOnlyAuditTablesAfterTheEvidenceLedger() throws Exception {
        String schema = "insurance_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway beforeInsurance = flyway(schema, MigrationVersion.fromVersion("20260902233000"));
            assertThat(beforeInsurance.migrate().migrationsExecuted).isEqualTo(20);
            assertThat(tableExists(schema, "insurance_case")).isFalse();

            Flyway insuranceMigration = flyway(schema, MigrationVersion.fromVersion("20260903003000"));
            assertThat(insuranceMigration.migrate().migrationsExecuted).isOne();
            assertThat(tableExists(schema, "insurance_case")).isTrue();
            assertThat(tableExists(schema, "insurance_evidence_access_audit")).isTrue();
            assertThat(tableExists(schema, "insurance_case_decision")).isTrue();
            assertThat(triggerExists(schema, "insurance_access_append_only")).isTrue();
            assertThat(triggerExists(schema, "insurance_decision_append_only")).isTrue();
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

    private static boolean triggerExists(String schema, String trigger) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
                var statement = connection.prepareStatement("""
                    SELECT EXISTS (
                        SELECT 1
                          FROM pg_trigger t
                          JOIN pg_class c ON c.oid = t.tgrelid
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = ? AND t.tgname = ? AND NOT t.tgisinternal
                    )
                    """)) {
            statement.setString(1, schema);
            statement.setString(2, trigger);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static void dropSchema(String schema) throws Exception {
        if (!schema.matches("insurance_upgrade_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Refusing to drop an unexpected schema");
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
