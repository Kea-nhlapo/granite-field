package za.co.trademesh.modules.risk.infrastructure;

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
import za.co.trademesh.modules.risk.domain.RiskEvidenceReference;
import za.co.trademesh.modules.risk.domain.RiskIndicator;
import za.co.trademesh.modules.risk.domain.RiskIndicatorState;
import za.co.trademesh.modules.risk.domain.RiskIndicatorTransition;
import za.co.trademesh.modules.risk.domain.RiskRepository;
import za.co.trademesh.modules.risk.domain.RiskRule;
import za.co.trademesh.modules.risk.domain.RiskSeverity;

@Repository
class JdbcRiskRepository implements RiskRepository {

    private static final String INDICATOR_COLUMNS = """
        id, shipment_id, business_id, rule_code, rule_version, severity,
        explanation, state, first_observed_at, last_observed_at, created_at, updated_at
        """;
    private static final String TRANSITION_COLUMNS = """
        id, indicator_id, command_id, input_fingerprint, from_state, to_state,
        actor_user_id, note, occurred_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcRiskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RiskIndicator upsertActive(RiskIndicator proposal) {
        UUID storedId = jdbcTemplate.queryForObject(
                """
            INSERT INTO risk_indicator (
                id, shipment_id, business_id, rule_code, rule_version, severity,
                explanation, state, first_observed_at, last_observed_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)
            ON CONFLICT (shipment_id, rule_code)
                WHERE state IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING')
            DO UPDATE SET
                last_observed_at = GREATEST(risk_indicator.last_observed_at, EXCLUDED.last_observed_at),
                updated_at = GREATEST(risk_indicator.updated_at, EXCLUDED.updated_at),
                explanation = EXCLUDED.explanation,
                rule_version = EXCLUDED.rule_version,
                severity = CASE
                    WHEN risk_indicator.severity = 'HIGH' OR EXCLUDED.severity = 'HIGH' THEN 'HIGH'
                    WHEN risk_indicator.severity = 'MEDIUM' OR EXCLUDED.severity = 'MEDIUM' THEN 'MEDIUM'
                    ELSE 'LOW'
                END
            RETURNING id
            """,
                UUID.class,
                proposal.id(),
                proposal.shipmentId(),
                proposal.businessId(),
                proposal.rule().name(),
                proposal.ruleVersion(),
                proposal.severity().name(),
                proposal.explanation(),
                time(proposal.firstObservedAt()),
                time(proposal.lastObservedAt()),
                time(proposal.createdAt()),
                time(proposal.updatedAt()));

        if (storedId.equals(proposal.id())) {
            saveTransition(storedId, proposal.transitions().getFirst(), 0);
        }
        for (RiskEvidenceReference evidence : proposal.evidence()) {
            jdbcTemplate.update(
                    """
                INSERT INTO risk_indicator_evidence (
                    indicator_id, evidence_type, evidence_id, observed_at
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, storedId, evidence.evidenceType(), evidence.evidenceId(), time(evidence.observedAt()));
        }
        return findById(storedId).orElseThrow();
    }

    @Override
    public Optional<RiskIndicator> findById(UUID indicatorId) {
        return find("SELECT " + INDICATOR_COLUMNS + " FROM risk_indicator WHERE id = ?", indicatorId);
    }

    @Override
    public Optional<RiskIndicator> findByIdForUpdate(UUID indicatorId) {
        return find("SELECT " + INDICATOR_COLUMNS + " FROM risk_indicator WHERE id = ? FOR UPDATE", indicatorId);
    }

    @Override
    public List<RiskIndicator> findByShipment(UUID shipmentId) {
        return jdbcTemplate
                .query(
                        "SELECT " + INDICATOR_COLUMNS
                                + " FROM risk_indicator WHERE shipment_id = ?"
                                + " ORDER BY first_observed_at DESC, id",
                        this::mapIndicatorBase,
                        shipmentId)
                .stream()
                .map(this::withDetails)
                .toList();
    }

    @Override
    public Optional<RiskIndicatorTransition> findTransitionByCommandId(UUID commandId) {
        return jdbcTemplate
                .query(
                        "SELECT " + TRANSITION_COLUMNS + " FROM risk_indicator_transition WHERE command_id = ?",
                        this::mapTransition,
                        commandId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean transition(
            UUID indicatorId, RiskIndicatorState expectedState, RiskIndicatorTransition transition, Instant updatedAt) {
        int updated = jdbcTemplate.update(
                """
            UPDATE risk_indicator
               SET state = ?, updated_at = ?
             WHERE id = ? AND state = ?
            """, transition.toState().name(), time(updatedAt), indicatorId, expectedState.name());
        if (updated != 1) {
            return false;
        }
        Integer sequence = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sequence) + 1, 0)" + " FROM risk_indicator_transition WHERE indicator_id = ?",
                Integer.class,
                indicatorId);
        saveTransition(indicatorId, transition, sequence == null ? 0 : sequence);
        return true;
    }

    private Optional<RiskIndicator> find(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapIndicatorBase, parameters).stream()
                .findFirst()
                .map(this::withDetails);
    }

    private RiskIndicator withDetails(RiskIndicator base) {
        List<RiskEvidenceReference> evidence = jdbcTemplate.query("""
            SELECT evidence_type, evidence_id, observed_at
              FROM risk_indicator_evidence
             WHERE indicator_id = ?
             ORDER BY observed_at, evidence_type, evidence_id
            """, this::mapEvidence, base.id());
        List<RiskIndicatorTransition> transitions = jdbcTemplate.query(
                "SELECT " + TRANSITION_COLUMNS
                        + " FROM risk_indicator_transition WHERE indicator_id = ? ORDER BY sequence",
                this::mapTransition,
                base.id());
        return new RiskIndicator(
                base.id(),
                base.shipmentId(),
                base.businessId(),
                base.rule(),
                base.ruleVersion(),
                base.severity(),
                base.explanation(),
                base.state(),
                base.firstObservedAt(),
                base.lastObservedAt(),
                base.createdAt(),
                base.updatedAt(),
                evidence,
                transitions);
    }

    private void saveTransition(UUID indicatorId, RiskIndicatorTransition transition, int sequence) {
        jdbcTemplate.update(
                """
            INSERT INTO risk_indicator_transition (
                id, indicator_id, command_id, input_fingerprint, sequence,
                from_state, to_state, actor_user_id, note, occurred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                transition.id(),
                indicatorId,
                transition.commandId(),
                transition.inputFingerprint(),
                sequence,
                nullableName(transition.fromState()),
                transition.toState().name(),
                transition.actorUserId(),
                transition.note(),
                time(transition.occurredAt()));
    }

    private RiskIndicator mapIndicatorBase(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RiskIndicator(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                RiskRule.valueOf(resultSet.getString("rule_code")),
                resultSet.getString("rule_version"),
                RiskSeverity.valueOf(resultSet.getString("severity")),
                resultSet.getString("explanation"),
                RiskIndicatorState.valueOf(resultSet.getString("state")),
                instant(resultSet, "first_observed_at"),
                instant(resultSet, "last_observed_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                List.of(),
                List.of());
    }

    private RiskEvidenceReference mapEvidence(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RiskEvidenceReference(
                resultSet.getString("evidence_type"),
                resultSet.getObject("evidence_id", UUID.class),
                instant(resultSet, "observed_at"));
    }

    private RiskIndicatorTransition mapTransition(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RiskIndicatorTransition(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("indicator_id", UUID.class),
                resultSet.getObject("command_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                nullableState(resultSet.getString("from_state")),
                RiskIndicatorState.valueOf(resultSet.getString("to_state")),
                resultSet.getObject("actor_user_id", UUID.class),
                resultSet.getString("note"),
                instant(resultSet, "occurred_at"));
    }

    private static RiskIndicatorState nullableState(String value) {
        return value == null ? null : RiskIndicatorState.valueOf(value);
    }

    private static String nullableName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
