package za.co.trademesh.modules.access.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.domain.UserAccount;
import za.co.trademesh.modules.access.domain.UserAccountRepository;
import za.co.trademesh.shared.security.AccountRole;

@Repository
class JdbcUserAccountRepository implements UserAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findByEmail(String normalizedEmail) {
        return findOne("""
            SELECT id, email, password_hash, enabled, created_at
            FROM access_user_account
            WHERE email = ?
            """, normalizedEmail);
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return findOne("""
            SELECT id, email, password_hash, enabled, created_at
            FROM access_user_account
            WHERE id = ?
            """, id);
    }

    @Override
    public boolean emailExists(String normalizedEmail) {
        Boolean exists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM access_user_account WHERE email = ?)
            """, Boolean.class, normalizedEmail);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void save(UserAccount account) {
        jdbcTemplate.update(
                """
            INSERT INTO access_user_account (id, email, password_hash, enabled, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
                account.id(),
                account.email(),
                account.passwordHash(),
                account.enabled(),
                OffsetDateTime.ofInstant(account.createdAt(), ZoneOffset.UTC));

        for (AccountRole role : account.roles()) {
            jdbcTemplate.update("""
                INSERT INTO access_user_role (user_id, role)
                VALUES (?, ?)
                """, account.id(), role.name());
        }
    }

    private Optional<UserAccount> findOne(String sql, Object parameter) {
        List<AccountRow> rows = jdbcTemplate.query(sql, this::mapAccount, parameter);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        AccountRow row = rows.getFirst();
        Set<AccountRole> roles = new LinkedHashSet<>(jdbcTemplate.queryForList("""
            SELECT role
            FROM access_user_role
            WHERE user_id = ?
            ORDER BY role
            """, String.class, row.id()).stream()
                .map(AccountRole::valueOf)
                .toList());

        return Optional.of(
                new UserAccount(row.id(), row.email(), row.passwordHash(), row.enabled(), row.createdAt(), roles));
    }

    private AccountRow mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AccountRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getBoolean("enabled"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private record AccountRow(
            UUID id, String email, String passwordHash, boolean enabled, java.time.Instant createdAt) {}
}
