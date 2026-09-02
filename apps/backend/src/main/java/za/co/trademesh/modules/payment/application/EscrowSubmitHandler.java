package za.co.trademesh.modules.payment.application;

import java.time.Clock;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.shared.events.outbox.OutboxHandler;
import za.co.trademesh.shared.events.outbox.OutboxMessage;

@Component
class EscrowSubmitHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final EscrowService escrow;
    private final EscrowOutboxRequests requests;
    private final MomoClient momo;
    private final Clock clock;

    EscrowSubmitHandler(
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
        return EscrowOutboxRequest.SUBMIT_TYPE;
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
            MomoClient.TransactionStatus known =
                    momo.getTransactionStatus(instruction.providerReference().toString(), instruction.product());
            if (known == MomoClient.TransactionStatus.SUCCESSFUL || known == MomoClient.TransactionStatus.FAILED) {
                escrow.complete(instruction.transactionId(), known);
                return;
            }
            if (known == MomoClient.TransactionStatus.PENDING) {
                escrow.markPending(instruction.transactionId());
                requests.poll(instruction.transactionId(), 0);
                return;
            }
            submit(instruction);
            escrow.markPending(instruction.transactionId());
            requests.poll(instruction.transactionId(), 0);
        } catch (MomoException failure) {
            if (failure.retryable()) {
                throw failure;
            }
            escrow.fail(instruction.transactionId(), failure.code());
        }
    }

    private void submit(EscrowService.ProviderInstruction instruction) {
        MomoClient.MoneyRequest request = new MomoClient.MoneyRequest(
                instruction.phoneNumber(),
                instruction.amount(),
                instruction.providerReference().toString());
        if (instruction.product() == MomoClient.Product.COLLECTIONS) {
            momo.requestToPay(request);
        } else {
            momo.transfer(request);
        }
    }
}
