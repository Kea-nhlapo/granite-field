package za.co.trademesh.shared.events.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * markDone, markForRetry, and markDead are all guarded by the claimed_at
 * token returned from claimBatch (see the "guard outbox outcome updates with
 * a claim ownership token" fix). This test exercises the failure side of that
 * guard directly: a worker whose claim has been superseded — the reaper
 * revoked it and a second worker re-claimed the row — must have its outcome
 * update no-op rather than silently applying to a claim it no longer owns.
 *
 * <p>A later review found the retry and dead-letter paths swallowed that
 * no-op with no log line at all, unlike the success path, which is the
 * observability half of the same gap; see OutboxWorker.warnIfClaimAlreadyRevoked.
 * This test covers the data-safety half: that the guard actually holds.
 */
class OutboxOwnershipTokenTest extends OutboxTestSupport {

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private OutboxSubmitter submitter;

    @Test
    void markForRetryDoesNotTouchARowReclaimedUnderAStaleToken() {
        UUID id = claimedMessage();
        Instant staleToken = claimedAtOf(id);

        // Simulate the reaper revoking the claim and a second worker
        // re-claiming it: claimed_at moves forward to a new token.
        Instant freshToken = staleToken.plusSeconds(60);
        jdbcTemplate.update(
            "UPDATE outbox_message SET claimed_at = ? WHERE id = ?",
            java.sql.Timestamp.from(freshToken), id);

        boolean updated = repository.markForRetry(id, staleToken, Instant.now(), "stale attempt");

        assertThat(updated).isFalse();
        assertThat(claimedAtOf(id))
            .as("the row must still belong to whoever holds the fresh token")
            .isEqualTo(freshToken);
        assertThat(statusOf(id)).isEqualTo("CLAIMED");
    }

    @Test
    void markDeadDoesNotTouchARowReclaimedUnderAStaleToken() {
        UUID id = claimedMessage();
        Instant staleToken = claimedAtOf(id);
        Instant freshToken = staleToken.plusSeconds(60);
        jdbcTemplate.update(
            "UPDATE outbox_message SET claimed_at = ? WHERE id = ?",
            java.sql.Timestamp.from(freshToken), id);

        boolean updated = repository.markDead(id, staleToken, "stale attempt");

        assertThat(updated).isFalse();
        assertThat(statusOf(id)).isEqualTo("CLAIMED");
    }

    @Test
    void markDoneSucceedsWhenTheTokenStillMatches() {
        UUID id = claimedMessage();

        boolean updated = repository.markDone(id, claimedAtOf(id));

        assertThat(updated).isTrue();
        assertThat(statusOf(id)).isEqualTo("DONE");
    }

    private UUID claimedMessage() {
        submitter.submit("test.ownership-token", "job-1", Map.of(), 1);
        UUID id = onlyMessageId();
        repository.claimBatch(10, Instant.now());
        return id;
    }

    private Instant claimedAtOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "SELECT claimed_at FROM outbox_message WHERE id = ?", java.sql.Timestamp.class, id)
            .toInstant();
    }
}
