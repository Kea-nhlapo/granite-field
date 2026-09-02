package za.co.trademesh.modules.aggregation.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.aggregation.domain.AggregationConstraint;
import za.co.trademesh.modules.aggregation.domain.AggregationConstraintResult;
import za.co.trademesh.modules.aggregation.domain.AggregationExclusionReason;
import za.co.trademesh.modules.aggregation.domain.AggregationOrderRole;
import za.co.trademesh.modules.aggregation.domain.AggregationThresholds;
import za.co.trademesh.modules.aggregation.domain.ConstraintOutcome;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestion;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestionRepository;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestionStatus;
import za.co.trademesh.modules.aggregation.domain.DemandOrderEvaluation;

@Repository
class JdbcDemandGroupSuggestionRepository implements DemandGroupSuggestionRepository {

    private static final String SUGGESTION_COLUMNS = """
        id, requested_by_business_id, anchor_order_id, status, algorithm_version,
        input_fingerprint, search_radius_meters, maximum_distance_meters,
        minimum_window_overlap_seconds, minimum_cargo_overlap_ratio,
        candidate_limit, score, created_by_user_id, created_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcDemandGroupSuggestionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(DemandGroupSuggestion suggestion, UUID clientRequestId) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO demand_group_suggestion (
                id, requested_by_business_id, anchor_order_id, client_request_id,
                status, algorithm_version, input_fingerprint, search_radius_meters,
                maximum_distance_meters, minimum_window_overlap_seconds,
                minimum_cargo_overlap_ratio, candidate_limit, score,
                created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                suggestion.id(),
                suggestion.requestedByBusinessId(),
                suggestion.anchorOrderId(),
                clientRequestId,
                suggestion.status().name(),
                suggestion.algorithmVersion(),
                suggestion.inputFingerprint(),
                suggestion.thresholds().searchRadiusMeters(),
                suggestion.thresholds().maximumDistanceMeters(),
                suggestion.thresholds().minimumWindowOverlap().toSeconds(),
                suggestion.thresholds().minimumCargoOverlapRatio(),
                suggestion.thresholds().candidateLimit(),
                suggestion.score(),
                suggestion.createdByUserId(),
                time(suggestion.createdAt()));
        if (written != 1) {
            return false;
        }
        for (DemandOrderEvaluation evaluation : suggestion.orderEvaluations()) {
            saveEvaluation(suggestion.id(), evaluation);
        }
        return true;
    }

    @Override
    public Optional<DemandGroupSuggestion> findById(UUID businessId, UUID suggestionId) {
        return one(
                "SELECT " + SUGGESTION_COLUMNS
                        + " FROM demand_group_suggestion WHERE requested_by_business_id = ? AND id = ?",
                businessId,
                suggestionId);
    }

    @Override
    public Optional<DemandGroupSuggestion> findByClientRequestId(UUID businessId, UUID clientRequestId) {
        return one(
                "SELECT " + SUGGESTION_COLUMNS
                        + " FROM demand_group_suggestion WHERE requested_by_business_id = ? AND client_request_id = ?",
                businessId,
                clientRequestId);
    }

    @Override
    public Optional<DemandGroupSuggestion> findActiveByFingerprint(UUID businessId, String inputFingerprint) {
        return one(
                "SELECT " + SUGGESTION_COLUMNS
                        + " FROM demand_group_suggestion WHERE requested_by_business_id = ?"
                        + " AND input_fingerprint = ? AND status = 'ACTIVE'",
                businessId,
                inputFingerprint);
    }

    private void saveEvaluation(UUID suggestionId, DemandOrderEvaluation evaluation) {
        jdbcTemplate.update(
                """
            INSERT INTO demand_group_order_evaluation (
                suggestion_id, order_id, buyer_business_id, role, included,
                destination_label, distance_meters, window_overlap_seconds,
                cargo_overlap_ratio, score
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                suggestionId,
                evaluation.orderId(),
                evaluation.buyerBusinessId(),
                evaluation.role().name(),
                evaluation.included(),
                evaluation.destinationLabel(),
                evaluation.distanceMeters(),
                evaluation.windowOverlapSeconds(),
                evaluation.cargoOverlapRatio(),
                evaluation.score());
        for (AggregationConstraintResult result : evaluation.constraintResults()) {
            jdbcTemplate.update(
                    """
                INSERT INTO demand_group_constraint_result (
                    suggestion_id, order_id, constraint_code, outcome,
                    exclusion_reason, explanation
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                    suggestionId,
                    evaluation.orderId(),
                    result.constraint().name(),
                    result.outcome().name(),
                    result.exclusionReason() == null
                            ? null
                            : result.exclusionReason().name(),
                    result.explanation());
        }
    }

    private Optional<DemandGroupSuggestion> one(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapSuggestion, parameters).stream().findFirst();
    }

    private DemandGroupSuggestion mapSuggestion(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID suggestionId = resultSet.getObject("id", UUID.class);
        List<DemandOrderEvaluation> evaluations = jdbcTemplate.query(
                """
            SELECT order_id, buyer_business_id, role, included, destination_label,
                   distance_meters, window_overlap_seconds, cargo_overlap_ratio, score
              FROM demand_group_order_evaluation
             WHERE suggestion_id = ?
             ORDER BY role, included DESC, score DESC, order_id
            """, (evaluationSet, evaluationNumber) -> mapEvaluation(suggestionId, evaluationSet), suggestionId);
        return new DemandGroupSuggestion(
                suggestionId,
                resultSet.getObject("requested_by_business_id", UUID.class),
                resultSet.getObject("anchor_order_id", UUID.class),
                DemandGroupSuggestionStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("algorithm_version"),
                resultSet.getString("input_fingerprint").strip(),
                new AggregationThresholds(
                        resultSet.getDouble("search_radius_meters"),
                        resultSet.getDouble("maximum_distance_meters"),
                        Duration.ofSeconds(resultSet.getLong("minimum_window_overlap_seconds")),
                        resultSet.getDouble("minimum_cargo_overlap_ratio"),
                        resultSet.getInt("candidate_limit")),
                resultSet.getDouble("score"),
                evaluations,
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private DemandOrderEvaluation mapEvaluation(UUID suggestionId, ResultSet resultSet) throws SQLException {
        UUID orderId = resultSet.getObject("order_id", UUID.class);
        List<AggregationConstraintResult> results =
                jdbcTemplate.query("""
            SELECT constraint_code, outcome, exclusion_reason, explanation
              FROM demand_group_constraint_result
             WHERE suggestion_id = ? AND order_id = ?
             ORDER BY constraint_code
            """, this::mapConstraintResult, suggestionId, orderId);
        return new DemandOrderEvaluation(
                orderId,
                resultSet.getObject("buyer_business_id", UUID.class),
                AggregationOrderRole.valueOf(resultSet.getString("role")),
                resultSet.getBoolean("included"),
                resultSet.getString("destination_label"),
                resultSet.getDouble("distance_meters"),
                resultSet.getLong("window_overlap_seconds"),
                resultSet.getDouble("cargo_overlap_ratio"),
                resultSet.getDouble("score"),
                results);
    }

    private AggregationConstraintResult mapConstraintResult(ResultSet resultSet, int rowNumber) throws SQLException {
        String exclusion = resultSet.getString("exclusion_reason");
        return new AggregationConstraintResult(
                AggregationConstraint.valueOf(resultSet.getString("constraint_code")),
                ConstraintOutcome.valueOf(resultSet.getString("outcome")),
                exclusion == null ? null : AggregationExclusionReason.valueOf(exclusion),
                resultSet.getString("explanation"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
