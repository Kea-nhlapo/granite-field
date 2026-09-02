package za.co.trademesh.modules.access.infrastructure;

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
import za.co.trademesh.modules.access.application.MomoSignIn;
import za.co.trademesh.modules.access.application.MomoSignInRepository;
import za.co.trademesh.modules.payment.application.MomoClient;

@Repository
class JdbcMomoSignInRepository implements MomoSignInRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcMomoSignInRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(MomoSignIn signIn) {
        jdbcTemplate.update(
                """
            INSERT INTO access_momo_sign_in (
                id, poll_token_hash, phone_number, provider_reference, status,
                expires_at, completed_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
                signIn.id(),
                signIn.pollTokenHash(),
                signIn.phoneNumber(),
                signIn.providerReference(),
                signIn.status().name(),
                utc(signIn.expiresAt()),
                utc(signIn.completedAt()),
                utc(signIn.createdAt()));
    }

    @Override
    public Optional<MomoSignIn> findByPollTokenHash(String pollTokenHash) {
        List<MomoSignIn> rows = jdbcTemplate.query("""
            SELECT id, poll_token_hash, phone_number, provider_reference, status,
                   expires_at, completed_at, created_at
            FROM access_momo_sign_in
            WHERE poll_token_hash = ?
            """, this::map, pollTokenHash);
        return rows.stream().findFirst();
    }

    @Override
    public void updateStatus(UUID id, MomoClient.ConsentStatus status) {
        jdbcTemplate.update("UPDATE access_momo_sign_in SET status = ? WHERE id = ?", status.name(), id);
    }

    @Override
    public boolean complete(UUID id, Instant completedAt) {
        return jdbcTemplate.update("""
                UPDATE access_momo_sign_in
                SET completed_at = ?
                WHERE id = ? AND completed_at IS NULL
                """, utc(completedAt), id) == 1;
    }

    private MomoSignIn map(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime completed = resultSet.getObject("completed_at", OffsetDateTime.class);
        return new MomoSignIn(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("poll_token_hash"),
                resultSet.getString("phone_number"),
                resultSet.getString("provider_reference"),
                MomoClient.ConsentStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("expires_at", OffsetDateTime.class).toInstant(),
                completed == null ? null : completed.toInstant(),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
