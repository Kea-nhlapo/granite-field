package za.co.trademesh.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.trademesh.support.PostgresIntegrationTest;

class DatabaseIntegrationTest extends PostgresIntegrationTest {

    private static final String POSTGIS_MIGRATION_VERSION = "20260901203000";

    /**
     * Probe tables live in their own schema, created and dropped around each
     * test. The container is a singleton shared by every integration test in
     * the JVM, so a table left behind in the public schema — which is what a
     * failed assertion before a trailing DROP would do — is state leaking into
     * whatever runs next.
     */
    private static final String PROBE_SCHEMA = "spatial_probe";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createProbeSchema() {
        jdbcTemplate.execute("CREATE SCHEMA " + PROBE_SCHEMA);
    }

    @AfterEach
    void dropProbeSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + PROBE_SCHEMA + " CASCADE");
    }

    @Test
    void flywayRunsOnStartupAndRecordsTheRequirePostgisMigration() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = ? AND success = true",
                Integer.class,
                POSTGIS_MIGRATION_VERSION);

        assertThat(applied)
                .as("the require-postgis migration is recorded as applied")
                .isEqualTo(1);
    }

    /**
     * Availability, not causation: the postgis image installs the extension
     * at initdb, so this cannot prove anything about how it got there — and
     * under ADR 0002 the migration no longer installs it at all. What the
     * migration guarantees is that a database provisioned WITHOUT PostGIS
     * fails at deploy time; that is asserted by PostgisRequirementTest.
     */
    @Test
    void postgisIsAvailableWithoutAnyManualSetup() {
        String version = jdbcTemplate.queryForObject("SELECT postgis_version()", String.class);

        assertThat(version).isNotBlank();
    }

    @Test
    void spatialColumnsAndDistanceQueriesWork() {
        jdbcTemplate.execute("CREATE TABLE " + PROBE_SCHEMA + ".location (position geography(Point, 4326))");
        jdbcTemplate.execute("INSERT INTO " + PROBE_SCHEMA + ".location (position) "
                + "VALUES (ST_GeogFromText('SRID=4326;POINT(28.05 -26.20)'))");

        Double metresToPretoria = jdbcTemplate.queryForObject(
                "SELECT ST_Distance(position, ST_GeogFromText('SRID=4326;POINT(28.19 -25.75)')) " + "FROM "
                        + PROBE_SCHEMA + ".location",
                Double.class);

        assertThat(metresToPretoria).isNotNull().isBetween(45_000.0, 60_000.0);
    }
}
