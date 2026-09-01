package za.co.trademesh.shared.events.outbox;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A worker killed mid-dispatch leaves its message CLAIMED. Without a reaper
 * that message is owned by a process that no longer exists, forever, and the
 * queue drains to a standstill that logs nothing — the failure looks like
 * "the job never ran" with a healthy application beside it.
 */
class OutboxReaperTest extends OutboxTestSupport {

    @Autowired
    private OutboxSubmitter submitter;

    @Autowired
    private OutboxWorker worker;

    @Test
    void returnsAMessageWhoseClaimOutlivedTheVisibilityTimeout() {
        UUID id = claimedMessageAbandonedFor("10 minutes");

        assertThat(worker.reapOnce()).isEqualTo(1);

        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT claimed_at FROM outbox_message WHERE id = ?", Object.class, id))
            .isNull();
    }

    @Test
    void leavesAClaimThatIsStillWithinTheVisibilityTimeout() {
        UUID id = claimedMessageAbandonedFor("30 seconds");

        assertThat(worker.reapOnce())
            .as("the default visibility timeout is 5 minutes")
            .isZero();

        assertThat(statusOf(id)).isEqualTo("CLAIMED");
    }

    /**
     * The claim that died already counted against the message. Counting it
     * again would let a run of unlucky restarts retire a message that never
     * actually failed.
     */
    @Test
    void doesNotChargeAnAttemptForTheClaimThatDied() {
        UUID id = claimedMessageAbandonedFor("10 minutes");
        jdbcTemplate.update("UPDATE outbox_message SET attempts = 3 WHERE id = ?", id);

        worker.reapOnce();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT attempts FROM outbox_message WHERE id = ?", Integer.class, id))
            .isEqualTo(3);
    }

    @Test
    void makesAReapedMessageClaimableAgain() {
        claimedMessageAbandonedFor("10 minutes");

        worker.reapOnce();

        assertThat(worker.pollOnce())
            .as("a recovered message is picked up by the next poll")
            .isEqualTo(1);
    }

    private UUID claimedMessageAbandonedFor(String interval) {
        submitter.submit("test.abandoned", "job-1", Map.of(), 1);
        UUID id = onlyMessageId();

        jdbcTemplate.update(
            "UPDATE outbox_message SET status = 'CLAIMED', attempts = 1, "
                + "claimed_at = now() - CAST(? AS interval) WHERE id = ?",
            interval, id);

        return id;
    }
}
