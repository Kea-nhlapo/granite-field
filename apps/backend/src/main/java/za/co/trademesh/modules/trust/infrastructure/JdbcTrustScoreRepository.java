package za.co.trademesh.modules.trust.infrastructure;

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
import za.co.trademesh.modules.trust.domain.TrustScoreRepository;
import za.co.trademesh.modules.trust.domain.TrustScoreSnapshot;

@Repository
class JdbcTrustScoreRepository implements TrustScoreRepository {

    private static final String COLUMNS = """
        business_id, provisional_score, verified_score, verification_schedule_mode,
        calculation_version, source_evidence_through_sequence, provisional_calculated_at,
        verified_calculated_at, next_verification_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcTrustScoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TrustScoreSnapshot> find(UUID businessId) {
        return jdbcTemplate
                .query("SELECT " + COLUMNS + " FROM trust_score_snapshot WHERE business_id = ?", this::map, businessId)
                .stream()
                .findFirst();
    }

    @Override
    public void save(TrustScoreSnapshot snapshot) {
        jdbcTemplate.update(
                """
                INSERT INTO trust_score_snapshot (
                    business_id, provisional_score, verified_score, verification_schedule_mode,
                    calculation_version, source_evidence_through_sequence, provisional_calculated_at,
                    verified_calculated_at, next_verification_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (business_id) DO UPDATE SET
                    provisional_score = EXCLUDED.provisional_score,
                    verified_score = EXCLUDED.verified_score,
                    verification_schedule_mode = EXCLUDED.verification_schedule_mode,
                    calculation_version = EXCLUDED.calculation_version,
                    source_evidence_through_sequence = EXCLUDED.source_evidence_through_sequence,
                    provisional_calculated_at = EXCLUDED.provisional_calculated_at,
                    verified_calculated_at = EXCLUDED.verified_calculated_at,
                    next_verification_at = EXCLUDED.next_verification_at
                """,
                snapshot.businessId(),
                snapshot.provisionalScore(),
                snapshot.verifiedScore(),
                snapshot.verificationScheduleMode(),
                snapshot.calculationVersion(),
                snapshot.sourceEvidenceThroughSequence(),
                time(snapshot.provisionalCalculatedAt()),
                time(snapshot.verifiedCalculatedAt()),
                time(snapshot.nextVerificationAt()));
    }

    @Override
    public List<UUID> findDueBusinessIds(Instant dueAt, int limit) {
        return jdbcTemplate.query(
                """
                SELECT business_id
                  FROM trust_score_snapshot
                 WHERE next_verification_at <= ?
                 ORDER BY next_verification_at, business_id
                 LIMIT ?
                """, (resultSet, rowNumber) -> resultSet.getObject("business_id", UUID.class), time(dueAt), limit);
    }

    private TrustScoreSnapshot map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TrustScoreSnapshot(
                resultSet.getObject("business_id", UUID.class),
                resultSet.getBigDecimal("provisional_score"),
                resultSet.getBigDecimal("verified_score"),
                resultSet.getString("verification_schedule_mode"),
                resultSet.getString("calculation_version"),
                resultSet.getLong("source_evidence_through_sequence"),
                instant(resultSet, "provisional_calculated_at"),
                instant(resultSet, "verified_calculated_at"),
                instant(resultSet, "next_verification_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
