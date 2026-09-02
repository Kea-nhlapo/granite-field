package za.co.trademesh.modules.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EscrowTransaction(
        UUID id,
        UUID escrowId,
        UUID commandId,
        EscrowTransactionType type,
        int sequence,
        UUID providerReference,
        String protectedPhone,
        BigDecimal amount,
        EscrowTransactionStatus status,
        Instant deadlineAt,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {}
