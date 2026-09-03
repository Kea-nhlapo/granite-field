package za.co.trademesh.modules.payment.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.payment.domain.Escrow;
import za.co.trademesh.modules.payment.domain.EscrowRepository;
import za.co.trademesh.modules.payment.domain.EscrowStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransaction;
import za.co.trademesh.modules.payment.domain.EscrowTransactionStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransactionType;

@Repository
class JdbcEscrowRepository implements EscrowRepository {

    private static final String ESCROW_COLUMNS = """
        id, shipment_id, business_id, supplier_profile_id, protected_supplier_phone, currency,
        agreed_amount, status, created_at, updated_at
        """;
    private static final String TRANSACTION_COLUMNS = """
        id, escrow_id, command_id, transaction_type, sequence, provider_reference,
        protected_phone, amount, status, deadline_at, failure_code, created_at, updated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcEscrowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean saveInitial(Escrow escrow, EscrowTransaction transaction) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO payment_escrow (
                    id, shipment_id, business_id, supplier_profile_id, protected_supplier_phone,
                    currency, agreed_amount, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (shipment_id, business_id) DO NOTHING
                """,
                escrow.id(),
                escrow.shipmentId(),
                escrow.businessId(),
                escrow.supplierProfileId(),
                escrow.protectedSupplierPhone(),
                escrow.currency(),
                escrow.agreedAmount(),
                escrow.status().name(),
                time(escrow.createdAt()),
                time(escrow.updatedAt()));
        if (inserted == 0) {
            return false;
        }
        if (!addTransaction(transaction)) {
            throw new IllegalStateException("Initial escrow transaction was not persisted");
        }
        return true;
    }

    @Override
    public Optional<Escrow> find(UUID businessId, UUID shipmentId) {
        return escrow("WHERE business_id = ? AND shipment_id = ?", businessId, shipmentId);
    }

    @Override
    public Optional<Escrow> findById(UUID escrowId) {
        return escrow("WHERE id = ?", escrowId);
    }

    @Override
    public Optional<Escrow> findForUpdate(UUID businessId, UUID shipmentId) {
        return escrow("WHERE business_id = ? AND shipment_id = ? FOR UPDATE", businessId, shipmentId);
    }

    @Override
    public Optional<EscrowTransaction> findTransaction(UUID transactionId) {
        return transaction("WHERE id = ?", transactionId);
    }

    @Override
    public Optional<EscrowTransaction> findTransactionByCommand(
            UUID escrowId, EscrowTransactionType type, UUID commandId) {
        return transaction(
                "WHERE escrow_id = ? AND transaction_type = ? AND command_id = ?", escrowId, type.name(), commandId);
    }

    @Override
    public Optional<EscrowTransaction> findLatestTransaction(UUID escrowId, EscrowTransactionType type) {
        return transaction(
                "WHERE escrow_id = ? AND transaction_type = ? ORDER BY sequence DESC LIMIT 1", escrowId, type.name());
    }

    @Override
    public List<EscrowTransaction> findTransactions(UUID escrowId) {
        return jdbcTemplate.query(
                "SELECT " + TRANSACTION_COLUMNS
                        + " FROM payment_escrow_transaction WHERE escrow_id = ? ORDER BY created_at, sequence",
                JdbcEscrowRepository::mapTransaction,
                escrowId);
    }

    @Override
    public List<EscrowTransaction> findRecentTransactionsForBusiness(UUID businessId, int limit) {
        String columns = Arrays.stream(TRANSACTION_COLUMNS.split(","))
                .map(column -> "t." + column.strip())
                .collect(java.util.stream.Collectors.joining(", "));
        return jdbcTemplate.query(
                """
                SELECT %s
                  FROM payment_escrow_transaction t
                  JOIN payment_escrow e ON e.id = t.escrow_id
                 WHERE e.business_id = ?
                 ORDER BY t.created_at DESC, t.sequence DESC
                 LIMIT ?
                """
                        .formatted(columns),
                JdbcEscrowRepository::mapTransaction,
                businessId,
                limit);
    }

    @Override
    public int nextSequence(UUID escrowId, EscrowTransactionType type) {
        Integer value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(sequence), -1) + 1
                  FROM payment_escrow_transaction
                 WHERE escrow_id = ? AND transaction_type = ?
                """, Integer.class, escrowId, type.name());
        return value == null ? 0 : value;
    }

    @Override
    public boolean addTransaction(EscrowTransaction transaction) {
        return jdbcTemplate.update(
                        """
                        INSERT INTO payment_escrow_transaction (
                            id, escrow_id, command_id, transaction_type, sequence,
                            provider_reference, protected_phone, amount, status,
                            deadline_at, failure_code, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                        transaction.id(),
                        transaction.escrowId(),
                        transaction.commandId(),
                        transaction.type().name(),
                        transaction.sequence(),
                        transaction.providerReference(),
                        transaction.protectedPhone(),
                        transaction.amount(),
                        transaction.status().name(),
                        time(transaction.deadlineAt()),
                        transaction.failureCode(),
                        time(transaction.createdAt()),
                        time(transaction.updatedAt()))
                == 1;
    }

    @Override
    public boolean updateEscrowStatus(UUID escrowId, EscrowStatus expected, EscrowStatus target, Instant updatedAt) {
        return jdbcTemplate.update("""
                        UPDATE payment_escrow
                           SET status = ?, updated_at = ?
                         WHERE id = ? AND status = ?
                        """, target.name(), time(updatedAt), escrowId, expected.name()) == 1;
    }

    @Override
    public boolean updateTransactionStatus(
            UUID transactionId,
            EscrowTransactionStatus expected,
            EscrowTransactionStatus target,
            String failureCode,
            Instant updatedAt) {
        return jdbcTemplate.update("""
                        UPDATE payment_escrow_transaction
                           SET status = ?, failure_code = ?, updated_at = ?
                         WHERE id = ? AND status = ?
                        """, target.name(), failureCode, time(updatedAt), transactionId, expected.name())
                == 1;
    }

    private Optional<Escrow> escrow(String where, Object... arguments) {
        return jdbcTemplate
                .query("SELECT " + ESCROW_COLUMNS + " FROM payment_escrow " + where, this::mapEscrow, arguments)
                .stream()
                .findFirst();
    }

    private Optional<EscrowTransaction> transaction(String where, Object... arguments) {
        return jdbcTemplate
                .query(
                        "SELECT " + TRANSACTION_COLUMNS + " FROM payment_escrow_transaction " + where,
                        JdbcEscrowRepository::mapTransaction,
                        arguments)
                .stream()
                .findFirst();
    }

    private Escrow mapEscrow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Escrow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                resultSet.getObject("supplier_profile_id", UUID.class),
                resultSet.getString("protected_supplier_phone"),
                resultSet.getString("currency"),
                resultSet.getBigDecimal("agreed_amount"),
                EscrowStatus.valueOf(resultSet.getString("status")),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static EscrowTransaction mapTransaction(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EscrowTransaction(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("escrow_id", UUID.class),
                resultSet.getObject("command_id", UUID.class),
                EscrowTransactionType.valueOf(resultSet.getString("transaction_type")),
                resultSet.getInt("sequence"),
                resultSet.getObject("provider_reference", UUID.class),
                resultSet.getString("protected_phone"),
                resultSet.getBigDecimal("amount"),
                EscrowTransactionStatus.valueOf(resultSet.getString("status")),
                instant(resultSet, "deadline_at"),
                resultSet.getString("failure_code"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
