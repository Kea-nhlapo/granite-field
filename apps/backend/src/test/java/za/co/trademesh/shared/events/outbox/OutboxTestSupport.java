package za.co.trademesh.shared.events.outbox;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import za.co.trademesh.support.PostgresIntegrationTest;

/**
 * Base for outbox integration tests.
 *
 * <p>The container is a JVM-wide singleton, and unlike the spatial probe tables
 * in DatabaseIntegrationTest the outbox table is created by Flyway in the
 * public schema — so a per-test schema cannot isolate it. Rows are deleted in
 * {@link AfterEach} instead, which runs even when an assertion fails partway
 * through. Cleaning up at the end of the test body would leave rows behind on
 * exactly the runs where isolation matters most.
 */
abstract class OutboxTestSupport extends PostgresIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearOutbox() {
        jdbcTemplate.execute("DELETE FROM outbox_message");
    }

    protected String statusOf(java.util.UUID id) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_message WHERE id = ?", String.class, id);
    }

    protected java.util.UUID onlyMessageId() {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM outbox_message", java.util.UUID.class);
    }

    protected int rowCount() {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_message", Integer.class);
    }
}
