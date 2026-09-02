package za.co.trademesh.modules.payment.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import za.co.trademesh.shared.events.outbox.OutboxSubmitter;

@Component
class EscrowOutboxRequests {

    private final OutboxSubmitter outbox;
    private final EscrowProperties properties;
    private final Clock clock;

    EscrowOutboxRequests(OutboxSubmitter outbox, EscrowProperties properties, Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    void submit(UUID transactionId) {
        outbox.submit(
                EscrowOutboxRequest.SUBMIT_TYPE,
                "escrow-submit:" + transactionId,
                new EscrowOutboxRequest(transactionId, 0),
                EscrowOutboxRequest.SCHEMA_VERSION);
    }

    void poll(UUID transactionId, int sequence) {
        outbox.submitAt(
                EscrowOutboxRequest.POLL_TYPE,
                "escrow-poll:" + transactionId + ":" + sequence,
                new EscrowOutboxRequest(transactionId, sequence),
                EscrowOutboxRequest.SCHEMA_VERSION,
                clock.instant().plus(properties.pollInterval()));
    }
}
