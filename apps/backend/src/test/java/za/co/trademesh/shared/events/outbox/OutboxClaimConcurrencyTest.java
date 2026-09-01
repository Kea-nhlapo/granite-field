package za.co.trademesh.shared.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the claim query is safe for two workers at once.
 *
 * <p>Driven through two explicit connections with hand-managed transactions
 * rather than two threads. A threaded version would pass or fail on timing:
 * run the claims sequentially and it passes vacuously, run them in parallel and
 * it is flaky. Holding transaction A open while B claims makes the overlap
 * certain, which is the only way this asserts anything about SKIP LOCKED.
 */
class OutboxClaimConcurrencyTest extends OutboxTestSupport {

    @Autowired
    private OutboxSubmitter submitter;

    @Autowired
    private DataSource dataSource;

    @Test
    void twoConcurrentClaimsTakeDisjointMessages() throws Exception {
        for (int i = 0; i < 6; i++) {
            submitter.submit("test.concurrent", "job-" + i, Map.of("n", i), 1);
        }

        try (Connection a = dataSource.getConnection();
                Connection b = dataSource.getConnection()) {

            a.setAutoCommit(false);
            b.setAutoCommit(false);

            List<UUID> claimedByA = claim(a, 3);
            // A has not committed. Its rows are locked; B must skip them
            // rather than block on them or take them a second time.
            List<UUID> claimedByB = claim(b, 3);

            a.commit();
            b.commit();

            assertThat(claimedByA).hasSize(3);
            assertThat(claimedByB).hasSize(3);

            Set<UUID> overlap = new HashSet<>(claimedByA);
            overlap.retainAll(claimedByB);
            assertThat(overlap).as("no message may be claimed by two workers").isEmpty();
        }
    }

    @Test
    void aSecondClaimFindsNothingOnceEveryMessageIsTaken() throws Exception {
        submitter.submit("test.concurrent", "job-0", Map.of(), 1);

        try (Connection a = dataSource.getConnection();
                Connection b = dataSource.getConnection()) {

            a.setAutoCommit(false);
            b.setAutoCommit(false);

            assertThat(claim(a, 10)).hasSize(1);
            assertThat(claim(b, 10)).as("the only message is already held by A").isEmpty();

            a.commit();
            b.commit();
        }
    }

    /** Mirrors OutboxRepository.claimBatch, driven on a caller-supplied connection. */
    private List<UUID> claim(Connection connection, int batchSize) throws Exception {
        String sql = """
            UPDATE outbox_message
               SET status = 'CLAIMED', claimed_at = ?, claim_token = ?,
                   attempts = attempts + 1, updated_at = ?
             WHERE id IN (
                   SELECT id FROM outbox_message
                    WHERE status = 'PENDING' AND available_at <= ?
                    ORDER BY available_at
                    LIMIT ?
                      FOR UPDATE SKIP LOCKED)
         RETURNING id
            """;

        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        List<UUID> claimed = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, now);
            statement.setObject(2, UUID.randomUUID());
            statement.setTimestamp(3, now);
            statement.setTimestamp(4, now);
            statement.setInt(5, batchSize);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    claimed.add(rs.getObject("id", UUID.class));
                }
            }
        }
        return claimed;
    }
}
