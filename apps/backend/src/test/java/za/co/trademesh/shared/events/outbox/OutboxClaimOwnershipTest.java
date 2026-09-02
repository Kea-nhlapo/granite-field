package za.co.trademesh.shared.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OutboxClaimOwnershipTest extends OutboxTestSupport {

    @Autowired
    private OutboxSubmitter submitter;

    @Autowired
    private OutboxRepository repository;

    @Test
    void anExpiredWorkerCannotCompleteAnotherWorkersReclaimedMessage() {
        submitter.submit("test.claim-owner", "job-1", Map.of(), 1);
        Instant firstClaimTime = Instant.now();
        OutboxMessage firstClaim = repository.claimBatch(1, firstClaimTime).getFirst();

        repository.reapExpiredClaims(firstClaimTime.plus(Duration.ofMinutes(10)), Duration.ofMinutes(5));
        OutboxMessage secondClaim = repository
                .claimBatch(1, firstClaimTime.plus(Duration.ofMinutes(10)))
                .getFirst();

        assertThat(secondClaim.claimToken()).isNotEqualTo(firstClaim.claimToken());
        assertThat(repository.markDone(firstClaim.id(), firstClaim.claimToken()))
                .isFalse();
        assertThat(statusOf(firstClaim.id())).isEqualTo("CLAIMED");
        assertThat(repository.markDone(secondClaim.id(), secondClaim.claimToken()))
                .isTrue();
        assertThat(statusOf(firstClaim.id())).isEqualTo("DONE");
    }
}
