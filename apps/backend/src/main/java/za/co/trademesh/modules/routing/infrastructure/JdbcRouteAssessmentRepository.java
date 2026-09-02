package za.co.trademesh.modules.routing.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.routing.domain.CandidateRouteScore;
import za.co.trademesh.modules.routing.domain.RouteAssessment;
import za.co.trademesh.modules.routing.domain.RouteAssessmentRepository;
import za.co.trademesh.modules.routing.domain.RouteFactor;
import za.co.trademesh.modules.routing.domain.RouteFactorScore;
import za.co.trademesh.modules.routing.domain.RouteOption;

@Repository
class JdbcRouteAssessmentRepository implements RouteAssessmentRepository {

    private static final String ASSESSMENT_COLUMNS = """
        id, requested_by_business_id, calculation_id, client_request_id,
        input_fingerprint, cargo_profile, algorithm_version,
        recommended_candidate_id, created_by_user_id, created_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcRouteAssessmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(RouteAssessment assessment) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO routing_assessment (
                id, requested_by_business_id, calculation_id, client_request_id,
                input_fingerprint, cargo_profile, algorithm_version,
                recommended_candidate_id, created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                assessment.id(),
                assessment.requestedByBusinessId(),
                assessment.calculationId(),
                assessment.clientRequestId(),
                assessment.inputFingerprint(),
                assessment.cargoProfile(),
                assessment.algorithmVersion(),
                assessment.recommendedCandidateId(),
                assessment.createdByUserId(),
                time(assessment.createdAt()));
        if (written != 1) {
            return false;
        }
        assessment
                .weights()
                .forEach((factor, weight) -> jdbcTemplate.update(
                        "INSERT INTO routing_assessment_weight (assessment_id, factor, weight) VALUES (?, ?, ?)",
                        assessment.id(),
                        factor.name(),
                        weight));
        assessment.candidates().forEach(candidate -> saveCandidate(assessment, candidate));
        return true;
    }

    @Override
    public Optional<RouteAssessment> findById(UUID businessId, UUID assessmentId) {
        return find(
                "SELECT " + ASSESSMENT_COLUMNS
                        + " FROM routing_assessment WHERE requested_by_business_id = ? AND id = ?",
                businessId,
                assessmentId);
    }

    @Override
    public Optional<RouteAssessment> findByRequestId(UUID businessId, UUID requestId) {
        return find(
                "SELECT " + ASSESSMENT_COLUMNS
                        + " FROM routing_assessment WHERE requested_by_business_id = ? AND client_request_id = ?",
                businessId,
                requestId);
    }

    private Optional<RouteAssessment> find(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapAssessment, parameters).stream().findFirst();
    }

    private void saveCandidate(RouteAssessment assessment, CandidateRouteScore candidate) {
        jdbcTemplate.update(
                """
            INSERT INTO routing_candidate_score (
                assessment_id, candidate_id, calculation_id, total_score, confidence
            ) VALUES (?, ?, ?, ?, ?)
            """,
                assessment.id(),
                candidate.candidateId(),
                assessment.calculationId(),
                candidate.totalScore(),
                candidate.confidence());
        candidate
                .factors()
                .forEach(factor -> jdbcTemplate.update(
                        """
            INSERT INTO routing_factor_score (
                assessment_id, candidate_id, factor, raw_value, raw_unit,
                normalized_value, weight, contribution, data_available
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                        assessment.id(),
                        candidate.candidateId(),
                        factor.factor().name(),
                        factor.rawValue(),
                        factor.rawUnit(),
                        factor.normalizedValue(),
                        factor.weight(),
                        factor.contribution(),
                        factor.dataAvailable()));
        candidate
                .options()
                .forEach(option -> jdbcTemplate.update("""
            INSERT INTO routing_candidate_option (assessment_id, candidate_id, option_type)
            VALUES (?, ?, ?)
            """, assessment.id(), candidate.candidateId(), option.name()));
        for (int index = 0; index < candidate.reasons().size(); index++) {
            jdbcTemplate.update(
                    """
                INSERT INTO routing_candidate_reason (assessment_id, candidate_id, sequence, reason)
                VALUES (?, ?, ?, ?)
                """,
                    assessment.id(),
                    candidate.candidateId(),
                    index,
                    candidate.reasons().get(index));
        }
    }

    private RouteAssessment mapAssessment(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID assessmentId = resultSet.getObject("id", UUID.class);
        EnumMap<RouteFactor, java.math.BigDecimal> weights = new EnumMap<>(RouteFactor.class);
        jdbcTemplate
                .query(
                        "SELECT factor, weight FROM routing_assessment_weight WHERE assessment_id = ?",
                        (weightSet, weightRow) -> Map.entry(
                                RouteFactor.valueOf(weightSet.getString("factor")), weightSet.getBigDecimal("weight")),
                        assessmentId)
                .forEach(entry -> weights.put(entry.getKey(), entry.getValue()));
        List<CandidateRouteScore> candidates =
                jdbcTemplate.query("""
            SELECT score.candidate_id, candidate.label, score.total_score, score.confidence
              FROM routing_candidate_score score
              JOIN routing_candidate candidate ON candidate.id = score.candidate_id
             WHERE score.assessment_id = ?
             ORDER BY candidate.sequence
            """, (scoreSet, scoreRow) -> mapCandidate(assessmentId, scoreSet), assessmentId);
        return new RouteAssessment(
                assessmentId,
                resultSet.getObject("requested_by_business_id", UUID.class),
                resultSet.getObject("calculation_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                resultSet.getString("cargo_profile"),
                resultSet.getString("algorithm_version"),
                weights,
                resultSet.getObject("recommended_candidate_id", UUID.class),
                candidates,
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private CandidateRouteScore mapCandidate(UUID assessmentId, ResultSet resultSet) throws SQLException {
        UUID candidateId = resultSet.getObject("candidate_id", UUID.class);
        List<RouteFactorScore> factors = jdbcTemplate
                .query(
                        """
            SELECT factor, raw_value, raw_unit, normalized_value, weight,
                   contribution, data_available
              FROM routing_factor_score
             WHERE assessment_id = ? AND candidate_id = ?
            """,
                        (factorSet, factorRow) -> new RouteFactorScore(
                                RouteFactor.valueOf(factorSet.getString("factor")),
                                factorSet.getBigDecimal("raw_value"),
                                factorSet.getString("raw_unit"),
                                factorSet.getBigDecimal("normalized_value"),
                                factorSet.getBigDecimal("weight"),
                                factorSet.getBigDecimal("contribution"),
                                factorSet.getBoolean("data_available")),
                        assessmentId,
                        candidateId)
                .stream()
                .sorted(Comparator.comparingInt(factor -> factor.factor().ordinal()))
                .toList();
        EnumSet<RouteOption> options = EnumSet.noneOf(RouteOption.class);
        jdbcTemplate
                .queryForList("""
                    SELECT option_type
                      FROM routing_candidate_option
                     WHERE assessment_id = ? AND candidate_id = ?
                    """, String.class, assessmentId, candidateId)
                .forEach(value -> options.add(RouteOption.valueOf(value)));
        List<String> reasons = jdbcTemplate.queryForList("""
            SELECT reason
              FROM routing_candidate_reason
             WHERE assessment_id = ? AND candidate_id = ?
             ORDER BY sequence
            """, String.class, assessmentId, candidateId);
        return new CandidateRouteScore(
                candidateId,
                resultSet.getString("label"),
                resultSet.getBigDecimal("total_score"),
                resultSet.getBigDecimal("confidence"),
                options,
                factors,
                reasons);
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
