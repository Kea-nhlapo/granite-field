package za.co.trademesh.modules.payment.application;

import java.time.Clock;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.shared.events.outbox.OutboxHandler;
import za.co.trademesh.shared.events.outbox.OutboxMessage;

@Component
class EscrowPollHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final EscrowService escrow;
    private final EscrowOutboxRequests requests;
    private final MomoClient momo;
    private final Clock clock;

    EscrowPollHandler(
            ObjectMapper objectMapper,
            EscrowService escrow,
            EscrowOutboxRequests requests,
            MomoClient momo,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.escrow = escrow;
        this.requests = requests;
        this.momo = momo;
        this.clock = clock;
    }

    @Override
    public String type() {
        return EscrowOutboxRequest.POLL_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        EscrowOutboxRequest requested = objectMapper.readValue(message.payload(), EscrowOutboxRequest.class);
        var instruction = escrow.providerInstruction(requested.transactionId()).orElse(null);
        if (instruction == null || instruction.status().finalState()) {
            return;
        }
        if (!clock.instant().isBefore(instruction.deadlineAt())) {
            escrow.timeOut(instruction.transactionId());
            return;
        }
        try {
            MomoClient.TransactionStatus status =
                    momo.getTransactionStatus(instruction.providerReference().toString(), instruction.product());
            if (status == MomoClient.TransactionStatus.PENDING || status == MomoClient.TransactionStatus.UNKNOWN) {
                requests.poll(instruction.transactionId(), requested.pollSequence() + 1);
            } else {
                escrow.complete(instruction.transactionId(), status);
            }
        } catch (MomoException failure) {
            if (failure.retryable()) {
                throw failure;
            }
            escrow.fail(instruction.transactionId(), failure.code());
        }
    }
}
