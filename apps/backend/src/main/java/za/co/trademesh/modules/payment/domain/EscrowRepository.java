package za.co.trademesh.modules.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscrowRepository {

    boolean saveInitial(Escrow escrow, EscrowTransaction transaction);

    Optional<Escrow> find(UUID businessId, UUID shipmentId);

    Optional<Escrow> findById(UUID escrowId);

    Optional<Escrow> findForUpdate(UUID businessId, UUID shipmentId);

    Optional<EscrowTransaction> findTransaction(UUID transactionId);

    Optional<EscrowTransaction> findTransactionByCommand(UUID escrowId, EscrowTransactionType type, UUID commandId);

    Optional<EscrowTransaction> findLatestTransaction(UUID escrowId, EscrowTransactionType type);

    List<EscrowTransaction> findTransactions(UUID escrowId);

    List<EscrowTransaction> findRecentTransactionsForBusiness(UUID businessId, int limit);

    int nextSequence(UUID escrowId, EscrowTransactionType type);

    boolean addTransaction(EscrowTransaction transaction);

    boolean updateEscrowStatus(UUID escrowId, EscrowStatus expected, EscrowStatus target, Instant updatedAt);

    boolean updateTransactionStatus(
            UUID transactionId,
            EscrowTransactionStatus expected,
            EscrowTransactionStatus target,
            String failureCode,
            Instant updatedAt);
}
