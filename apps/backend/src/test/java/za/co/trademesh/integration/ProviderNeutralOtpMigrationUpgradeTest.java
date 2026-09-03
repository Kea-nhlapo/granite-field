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

class ProviderNeutralOtpMigrationUpgradeTest extends PostgresIntegrationTest {

    @Test
    void replacesTheProviderSpecificOtpVerificationMarker() throws Exception {
        String schema = "otp_provider_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway beforeProviderNeutralOtp = flyway(schema, MigrationVersion.fromVersion("20260903190000"));
            assertThat(beforeProviderNeutralOtp.migrate().migrationsExecuted).isEqualTo(27);

            UUID userId = UUID.randomUUID();
            try (Connection connection = POSTGRES.createConnection("");
                    Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + schema);
                statement.execute("""
                    INSERT INTO access_user_account (id, enabled, created_at)
                    VALUES ('%s', TRUE, CURRENT_TIMESTAMP)
                    """.formatted(userId));
                statement.execute("""
                    INSERT INTO access_phone_identity (
                        phone_number, user_id, verification_method, verified_at
                    ) VALUES ('+27821234567', '%s', 'TWILIO_OTP', CURRENT_TIMESTAMP)
                    """.formatted(userId));
            }

            Flyway providerNeutralOtp = flyway(schema, MigrationVersion.fromVersion("20260903200000"));
            assertThat(providerNeutralOtp.migrate().migrationsExecuted).isOne();

            try (Connection connection = POSTGRES.createConnection("");
                    Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + schema);
                try (ResultSet result = statement.executeQuery(
                        "SELECT verification_method FROM access_phone_identity WHERE user_id = '" + userId + "'")) {
                    result.next();
                    assertThat(result.getString(1)).isEqualTo("OTP");
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

    private static void dropSchema(String schema) throws Exception {
        if (!schema.matches("otp_provider_upgrade_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Refusing to drop an unexpected schema");
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
