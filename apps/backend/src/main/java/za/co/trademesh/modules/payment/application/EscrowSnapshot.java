package za.co.trademesh.modules.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.payment.domain.Escrow;
import za.co.trademesh.modules.payment.domain.EscrowStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransaction;
import za.co.trademesh.modules.payment.domain.EscrowTransactionStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransactionType;

public record EscrowSnapshot(
        UUID escrowId,
        UUID shipmentId,
        UUID businessId,
        String currency,
        BigDecimal agreedAmount,
        EscrowStatus status,
        Instant updatedAt,
        List<TransactionSnapshot> transactions) {

    public EscrowSnapshot {
        transactions = List.copyOf(transactions);
    }

    static EscrowSnapshot from(Escrow escrow, List<EscrowTransaction> transactions) {
        return new EscrowSnapshot(
                escrow.id(),
                escrow.shipmentId(),
                escrow.businessId(),
                escrow.currency(),
                escrow.agreedAmount(),
                escrow.status(),
                escrow.updatedAt(),
                transactions.stream().map(TransactionSnapshot::from).toList());
    }

    public record TransactionSnapshot(
            UUID transactionId,
            EscrowTransactionType type,
            int sequence,
            BigDecimal amount,
            EscrowTransactionStatus status,
            String failureCode,
            Instant updatedAt) {

        static TransactionSnapshot from(EscrowTransaction transaction) {
            return new TransactionSnapshot(
                    transaction.id(),
                    transaction.type(),
                    transaction.sequence(),
                    transaction.amount(),
                    transaction.status(),
                    transaction.failureCode(),
                    transaction.updatedAt());
        }
    }
}
