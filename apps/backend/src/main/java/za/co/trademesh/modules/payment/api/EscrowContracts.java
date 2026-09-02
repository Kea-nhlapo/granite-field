package za.co.trademesh.modules.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.payment.application.EscrowSnapshot;
import za.co.trademesh.modules.payment.domain.EscrowStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransactionStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransactionType;

final class EscrowContracts {

    private EscrowContracts() {}

    record RetryEscrowRequest(
            @NotNull UUID businessId, @NotNull UUID requestId) {}

    record ReleaseEscrowRequest(
            @NotNull UUID businessId,
            @NotNull UUID requestId,

            @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4)
            BigDecimal resolvedAmount) {}

    record EscrowResponse(
            UUID escrowId,
            UUID shipmentId,
            UUID businessId,
            String currency,
            BigDecimal agreedAmount,
            EscrowStatus status,
            Instant updatedAt,
            List<EscrowTransactionResponse> transactions) {

        static EscrowResponse from(EscrowSnapshot snapshot) {
            return new EscrowResponse(
                    snapshot.escrowId(),
                    snapshot.shipmentId(),
                    snapshot.businessId(),
                    snapshot.currency(),
                    snapshot.agreedAmount(),
                    snapshot.status(),
                    snapshot.updatedAt(),
                    snapshot.transactions().stream()
                            .map(EscrowTransactionResponse::from)
                            .toList());
        }
    }

    record EscrowTransactionResponse(
            UUID transactionId,
            EscrowTransactionType type,
            int sequence,
            BigDecimal amount,
            EscrowTransactionStatus status,
            String failureCode,
            Instant updatedAt) {

        static EscrowTransactionResponse from(EscrowSnapshot.TransactionSnapshot transaction) {
            return new EscrowTransactionResponse(
                    transaction.transactionId(),
                    transaction.type(),
                    transaction.sequence(),
                    transaction.amount(),
                    transaction.status(),
                    transaction.failureCode(),
                    transaction.updatedAt());
        }
    }
}
