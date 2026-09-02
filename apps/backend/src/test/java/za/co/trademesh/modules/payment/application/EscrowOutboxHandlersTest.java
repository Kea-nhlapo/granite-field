package za.co.trademesh.modules.payment.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.modules.payment.domain.EscrowTransactionStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransactionType;
import za.co.trademesh.shared.events.outbox.OutboxMessage;

class EscrowOutboxHandlersTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void submitChecksTheReferenceBeforeRequestingPaymentAndSchedulesPolling() {
        UUID transactionId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        ObjectMapper mapper = mock(ObjectMapper.class);
        EscrowService escrow = mock(EscrowService.class);
        EscrowOutboxRequests requests = mock(EscrowOutboxRequests.class);
        MomoClient momo = mock(MomoClient.class);
        OutboxMessage message = mock(OutboxMessage.class);
        when(message.payload()).thenReturn("{}");
        when(mapper.readValue("{}", EscrowOutboxRequest.class)).thenReturn(new EscrowOutboxRequest(transactionId, 0));
        when(escrow.providerInstruction(transactionId))
                .thenReturn(Optional.of(new EscrowService.ProviderInstruction(
                        transactionId,
                        providerReference,
                        EscrowTransactionType.LOCK,
                        "+27825550100",
                        new BigDecimal("8500.00"),
                        EscrowTransactionStatus.REQUESTED,
                        NOW.plusSeconds(120))));
        when(momo.getTransactionStatus(providerReference.toString(), MomoClient.Product.COLLECTIONS))
                .thenReturn(MomoClient.TransactionStatus.UNKNOWN);
        when(momo.requestToPay(any())).thenReturn(providerReference.toString());
        var handler = new EscrowSubmitHandler(mapper, escrow, requests, momo, Clock.fixed(NOW, ZoneOffset.UTC));

        handler.handle(message);

        verify(momo)
                .requestToPay(new MomoClient.MoneyRequest(
                        "+27825550100", new BigDecimal("8500.00"), providerReference.toString()));
        verify(escrow).markPending(transactionId);
        verify(requests).poll(transactionId, 0);
    }

    @Test
    void submitDoesNotRepeatAProviderTransactionThatAlreadyExists() {
        UUID transactionId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        ObjectMapper mapper = mock(ObjectMapper.class);
        EscrowService escrow = mock(EscrowService.class);
        EscrowOutboxRequests requests = mock(EscrowOutboxRequests.class);
        MomoClient momo = mock(MomoClient.class);
        OutboxMessage message = mock(OutboxMessage.class);
        when(message.payload()).thenReturn("{}");
        when(mapper.readValue("{}", EscrowOutboxRequest.class)).thenReturn(new EscrowOutboxRequest(transactionId, 0));
        when(escrow.providerInstruction(transactionId))
                .thenReturn(Optional.of(new EscrowService.ProviderInstruction(
                        transactionId,
                        providerReference,
                        EscrowTransactionType.LOCK,
                        "+27825550100",
                        new BigDecimal("8500.00"),
                        EscrowTransactionStatus.REQUESTED,
                        NOW.plusSeconds(120))));
        when(momo.getTransactionStatus(providerReference.toString(), MomoClient.Product.COLLECTIONS))
                .thenReturn(MomoClient.TransactionStatus.SUCCESSFUL);
        var handler = new EscrowSubmitHandler(mapper, escrow, requests, momo, Clock.fixed(NOW, ZoneOffset.UTC));

        handler.handle(message);

        verify(momo, never()).requestToPay(any());
        verify(escrow).complete(transactionId, MomoClient.TransactionStatus.SUCCESSFUL);
        verify(requests, never()).poll(any(), eq(0));
    }

    @Test
    void pollTimesOutWithoutCallingTheProviderAfterTheDeadline() {
        UUID transactionId = UUID.randomUUID();
        ObjectMapper mapper = mock(ObjectMapper.class);
        EscrowService escrow = mock(EscrowService.class);
        EscrowOutboxRequests requests = mock(EscrowOutboxRequests.class);
        MomoClient momo = mock(MomoClient.class);
        OutboxMessage message = mock(OutboxMessage.class);
        when(message.payload()).thenReturn("{}");
        when(mapper.readValue("{}", EscrowOutboxRequest.class)).thenReturn(new EscrowOutboxRequest(transactionId, 4));
        when(escrow.providerInstruction(transactionId))
                .thenReturn(Optional.of(new EscrowService.ProviderInstruction(
                        transactionId,
                        UUID.randomUUID(),
                        EscrowTransactionType.RELEASE,
                        "+27825550100",
                        new BigDecimal("8000.00"),
                        EscrowTransactionStatus.PENDING,
                        NOW)));
        var handler = new EscrowPollHandler(mapper, escrow, requests, momo, Clock.fixed(NOW, ZoneOffset.UTC));

        handler.handle(message);

        verify(escrow).timeOut(transactionId);
        verify(momo, never()).getTransactionStatus(any(), any());
    }
}
