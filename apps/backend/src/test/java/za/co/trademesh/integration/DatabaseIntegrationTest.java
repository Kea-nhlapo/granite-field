package za.co.trademesh.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.trademesh.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseIntegrationTest extends PostgresIntegrationTest {

    private static final String POSTGIS_MIGRATION_VERSION = "20260901203000";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayRunsOnStartupAndRecordsTheEnablePostgisMigration() {
        Integer applied = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = ? AND success = true",
            Integer.class, POSTGIS_MIGRATION_VERSION);

        assertThat(applied)
            .as("the enable-postgis migration is recorded as applied")
            .isEqualTo(1);
    }

    /**
     * Availability, not causation: the postgis image also enables the extension
     * in the database it creates at startup, so this cannot prove the migration
     * was what enabled it. The migration is what guarantees any OTHER database —
     * one created by hand, or a fresh environment — ends up the same. That the
     * migration ran at all is asserted above.
     */
    @Test
    void postgisIsAvailableWithoutAnyManualSetup() {
        String version = jdbcTemplate.queryForObject("SELECT postgis_version()", String.class);

        assertThat(version).isNotBlank();
    }

    @Test
    void spatialColumnsAndDistanceQueriesWork() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS spatial_probe");
        jdbcTemplate.execute("CREATE TABLE spatial_probe (position geography(Point, 4326))");
        jdbcTemplate.execute(
            "INSERT INTO spatial_probe (position) VALUES (ST_GeogFromText('SRID=4326;POINT(28.05 -26.20)'))");

        Double metresToPretoria = jdbcTemplate.queryForObject(
            "SELECT ST_Distance(position, ST_GeogFromText('SRID=4326;POINT(28.19 -25.75)')) FROM spatial_probe",
            Double.class);

        jdbcTemplate.execute("DROP TABLE spatial_probe");

        assertThat(metresToPretoria).isNotNull().isBetween(45_000.0, 60_000.0);
    }
}
