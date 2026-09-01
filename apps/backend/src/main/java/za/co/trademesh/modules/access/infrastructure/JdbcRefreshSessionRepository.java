package za.co.trademesh.modules.access.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.domain.RefreshSession;
import za.co.trademesh.modules.access.domain.RefreshSessionRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcRefreshSessionRepository implements RefreshSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcRefreshSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(RefreshSession session) {
        jdbcTemplate.update("""
            INSERT INTO access_refresh_session (
                id, user_id, token_hash, expires_at, created_at
            ) VALUES (?, ?, ?, ?, ?)
            """,
            session.id(),
            session.userId(),
            session.tokenHash(),
            toOffset(session.expiresAt()),
            toOffset(session.createdAt()));
    }

    @Override
    public Optional<RefreshSession> findActiveByTokenHash(String tokenHash, Instant now) {
        List<RefreshSession> sessions = jdbcTemplate.query("""
            SELECT id, user_id, token_hash, expires_at, created_at
            FROM access_refresh_session
            WHERE token_hash = ?
              AND revoked_at IS NULL
              AND expires_at > ?
            """,
            (resultSet, rowNumber) -> new RefreshSession(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("token_hash"),
                resultSet.getObject("expires_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()),
            tokenHash,
            toOffset(now));
        return sessions.stream().findFirst();
    }

    @Override
    public boolean revokeAndReplace(UUID currentSessionId, UUID replacementSessionId, Instant revokedAt) {
        return jdbcTemplate.update("""
            UPDATE access_refresh_session
            SET revoked_at = ?, replaced_by_id = ?
            WHERE id = ? AND revoked_at IS NULL
            """, toOffset(revokedAt), replacementSessionId, currentSessionId) == 1;
    }

    @Override
    public void revokeByTokenHash(String tokenHash, Instant revokedAt) {
        jdbcTemplate.update("""
            UPDATE access_refresh_session
            SET revoked_at = ?
            WHERE token_hash = ? AND revoked_at IS NULL
            """, toOffset(revokedAt), tokenHash);
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
