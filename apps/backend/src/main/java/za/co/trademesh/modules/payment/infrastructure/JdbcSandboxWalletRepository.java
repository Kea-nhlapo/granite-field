package za.co.trademesh.modules.payment.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.payment.domain.SandboxWallet;
import za.co.trademesh.modules.payment.domain.SandboxWalletEntry;
import za.co.trademesh.modules.payment.domain.SandboxWalletRepository;

@Repository
class JdbcSandboxWalletRepository implements SandboxWalletRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcSandboxWalletRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean create(
            UUID userId, String displayName, String currency, java.math.BigDecimal openingBalance, Instant now) {
        return jdbcTemplate.update("""
            INSERT INTO payment_sandbox_wallet (
                user_id, display_name, currency, available_balance, held_balance, created_at, updated_at
            ) VALUES (?, ?, ?, ?, 0, ?, ?)
            ON CONFLICT (user_id) DO NOTHING
            """, userId, displayName, currency, openingBalance, time(now), time(now)) == 1;
    }

    @Override
    public Optional<SandboxWallet> find(UUID userId) {
        return one("""
            SELECT user_id, display_name, currency, available_balance, held_balance, updated_at
              FROM payment_sandbox_wallet
             WHERE user_id = ?
            """, userId);
    }

    @Override
    public Optional<SandboxWallet> findForUpdate(UUID userId) {
        return one("""
            SELECT user_id, display_name, currency, available_balance, held_balance, updated_at
              FROM payment_sandbox_wallet
             WHERE user_id = ?
             FOR UPDATE
            """, userId);
    }

    @Override
    public boolean entryExists(String referenceKey) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payment_sandbox_wallet_entry WHERE reference_key = ?)",
                Boolean.class,
                referenceKey);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void update(
            UUID userId, java.math.BigDecimal availableBalance, java.math.BigDecimal heldBalance, Instant now) {
        jdbcTemplate.update("""
            UPDATE payment_sandbox_wallet
               SET available_balance = ?, held_balance = ?, updated_at = ?
             WHERE user_id = ?
            """, availableBalance, heldBalance, time(now), userId);
    }

    @Override
    public void add(SandboxWalletEntry entry) {
        jdbcTemplate.update(
                """
            INSERT INTO payment_sandbox_wallet_entry (
                id, user_id, reference_key, entry_type, available_delta, held_delta,
                available_balance_after, held_balance_after, description, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                entry.id(),
                entry.userId(),
                entry.referenceKey(),
                entry.type().name(),
                entry.availableDelta(),
                entry.heldDelta(),
                entry.availableBalanceAfter(),
                entry.heldBalanceAfter(),
                entry.description(),
                time(entry.createdAt()));
    }

    @Override
    public List<SandboxWalletEntry> entries(UUID userId, int limit) {
        return jdbcTemplate.query("""
            SELECT id, user_id, reference_key, entry_type, available_delta, held_delta,
                   available_balance_after, held_balance_after, description, created_at
              FROM payment_sandbox_wallet_entry
             WHERE user_id = ?
             ORDER BY created_at DESC, id DESC
             LIMIT ?
            """, this::mapEntry, userId, limit);
    }

    private Optional<SandboxWallet> one(String sql, UUID userId) {
        List<SandboxWallet> rows = jdbcTemplate.query(sql, this::mapWallet, userId);
        return rows.stream().findFirst();
    }

    private SandboxWallet mapWallet(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SandboxWallet(
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getString("currency"),
                resultSet.getBigDecimal("available_balance"),
                resultSet.getBigDecimal("held_balance"),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private SandboxWalletEntry mapEntry(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SandboxWalletEntry(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("reference_key"),
                SandboxWalletEntry.EntryType.valueOf(resultSet.getString("entry_type")),
                resultSet.getBigDecimal("available_delta"),
                resultSet.getBigDecimal("held_delta"),
                resultSet.getBigDecimal("available_balance_after"),
                resultSet.getBigDecimal("held_balance_after"),
                resultSet.getString("description"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
