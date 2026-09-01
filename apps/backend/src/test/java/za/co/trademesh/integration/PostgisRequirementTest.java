package za.co.trademesh.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The require-postgis migration only earns its place if it actually fails on a
 * database without PostGIS. Every other test in the suite runs against the
 * postgis image, where the extension is installed at initdb and the migration
 * can never take its failure branch.
 *
 * <p>Flyway is driven directly rather than through a Spring context: the aim is
 * to test the migration, and wiring a second application context to a second
 * container would add a fragile context-cache key and test the wiring instead.
 */
class PostgisRequirementTest {

    /** Deliberately NOT PostgresIntegrationTest.POSTGRES_IMAGE — plain Postgres has no PostGIS. */
    private static final String PLAIN_POSTGRES_IMAGE = "postgres:17";

    private static PostgreSQLContainer plainPostgres;

    @BeforeAll
    static void startPlainPostgres() {
        plainPostgres = new PostgreSQLContainer(PLAIN_POSTGRES_IMAGE);
        plainPostgres.start();
    }

    @AfterAll
    static void stopPlainPostgres() {
        plainPostgres.stop();
    }

    @Test
    void migrationFailsOnADatabaseProvisionedWithoutPostgis() {
        Flyway flyway = Flyway.configure()
                .dataSource(plainPostgres.getJdbcUrl(), plainPostgres.getUsername(), plainPostgres.getPassword())
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .load();

        assertThatThrownBy(flyway::migrate)
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("PostGIS is not installed in this database")
                .as("the failure must name the provisioning step, not just fail")
                .hasStackTraceContaining("CREATE EXTENSION postgis");
    }
}
