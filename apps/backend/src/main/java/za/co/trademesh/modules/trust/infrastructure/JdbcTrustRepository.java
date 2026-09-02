package za.co.trademesh.modules.trust.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.trust.domain.PublicTrustSummary;
import za.co.trademesh.modules.trust.domain.TrustHistoryBand;
import za.co.trademesh.modules.trust.domain.TrustRepository;

@Repository
class JdbcTrustRepository implements TrustRepository {

    private static final String COLUMNS = """
        business_id, registry_verified, identity_verified, completed_transaction_count,
        successful_delivery_count, delivery_success_rate, average_rating, rating_count,
        history_band, calculation_version, source_evidence_through_sequence, calculated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcTrustRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PublicTrustSummary> find(UUID businessId) {
        return jdbcTemplate
                .query("SELECT " + COLUMNS + " FROM trust_public_summary WHERE business_id = ?", this::map, businessId)
                .stream()
                .findFirst();
    }

    @Override
    public void save(PublicTrustSummary summary) {
        jdbcTemplate.update(
                """
                INSERT INTO trust_public_summary (
                    business_id, registry_verified, identity_verified, completed_transaction_count,
                    successful_delivery_count, delivery_success_rate, average_rating, rating_count,
                    history_band, calculation_version, source_evidence_through_sequence, calculated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (business_id) DO UPDATE SET
                    registry_verified = EXCLUDED.registry_verified,
                    identity_verified = EXCLUDED.identity_verified,
                    completed_transaction_count = EXCLUDED.completed_transaction_count,
                    successful_delivery_count = EXCLUDED.successful_delivery_count,
                    delivery_success_rate = EXCLUDED.delivery_success_rate,
                    average_rating = EXCLUDED.average_rating,
                    rating_count = EXCLUDED.rating_count,
                    history_band = EXCLUDED.history_band,
                    calculation_version = EXCLUDED.calculation_version,
                    source_evidence_through_sequence = EXCLUDED.source_evidence_through_sequence,
                    calculated_at = EXCLUDED.calculated_at
                WHERE (trust_public_summary.registry_verified,
                       trust_public_summary.identity_verified,
                       trust_public_summary.completed_transaction_count,
                       trust_public_summary.successful_delivery_count,
                       trust_public_summary.delivery_success_rate,
                       trust_public_summary.average_rating,
                       trust_public_summary.rating_count,
                       trust_public_summary.history_band,
                       trust_public_summary.calculation_version,
                       trust_public_summary.source_evidence_through_sequence)
                    IS DISTINCT FROM
                      (EXCLUDED.registry_verified,
                       EXCLUDED.identity_verified,
                       EXCLUDED.completed_transaction_count,
                       EXCLUDED.successful_delivery_count,
                       EXCLUDED.delivery_success_rate,
                       EXCLUDED.average_rating,
                       EXCLUDED.rating_count,
                       EXCLUDED.history_band,
                       EXCLUDED.calculation_version,
                       EXCLUDED.source_evidence_through_sequence)
                """,
                summary.businessId(),
                summary.registryVerified(),
                summary.identityVerified(),
                summary.completedTransactionCount(),
                summary.successfulDeliveryCount(),
                summary.deliverySuccessRate(),
                summary.averageRating(),
                summary.ratingCount(),
                summary.historyBand().name(),
                summary.calculationVersion(),
                summary.sourceEvidenceThroughSequence(),
                time(summary.calculatedAt()));
    }

    private PublicTrustSummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublicTrustSummary(
                resultSet.getObject("business_id", UUID.class),
                resultSet.getBoolean("registry_verified"),
                resultSet.getBoolean("identity_verified"),
                resultSet.getInt("completed_transaction_count"),
                resultSet.getInt("successful_delivery_count"),
                resultSet.getBigDecimal("delivery_success_rate"),
                resultSet.getBigDecimal("average_rating"),
                resultSet.getInt("rating_count"),
                TrustHistoryBand.valueOf(resultSet.getString("history_band")),
                resultSet.getString("calculation_version"),
                resultSet.getLong("source_evidence_through_sequence"),
                instant(resultSet, "calculated_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
