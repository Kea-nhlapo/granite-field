package za.co.trademesh.modules.insurance.infrastructure;

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
import za.co.trademesh.modules.insurance.domain.InsuranceCase;
import za.co.trademesh.modules.insurance.domain.InsuranceDecision;
import za.co.trademesh.modules.insurance.domain.InsuranceDecisionOutcome;
import za.co.trademesh.modules.insurance.domain.InsuranceEvidenceAccess;
import za.co.trademesh.modules.insurance.domain.InsurancePurpose;
import za.co.trademesh.modules.insurance.domain.InsuranceRepository;

@Repository
class JdbcInsuranceRepository implements InsuranceRepository {

    private static final String CASE_COLUMNS = """
        id, client_request_id, input_fingerprint, shipment_id, business_id, purpose,
        assigned_insurer_user_id, created_by_user_id, created_at
        """;
    private static final String DECISION_COLUMNS = """
        id, case_id, command_id, input_fingerprint, outcome, note, decided_by_user_id, decided_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcInsuranceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean saveCase(InsuranceCase insuranceCase) {
        return jdbcTemplate.update(
                        """
            INSERT INTO insurance_case (
                id, client_request_id, input_fingerprint, shipment_id, business_id, purpose,
                assigned_insurer_user_id, created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        insuranceCase.id(),
                        insuranceCase.clientRequestId(),
                        insuranceCase.inputFingerprint(),
                        insuranceCase.shipmentId(),
                        insuranceCase.businessId(),
                        insuranceCase.purpose().name(),
                        insuranceCase.assignedInsurerUserId(),
                        insuranceCase.createdByUserId(),
                        time(insuranceCase.createdAt()))
                == 1;
    }

    @Override
    public Optional<InsuranceCase> findCase(UUID caseId) {
        return oneCase("SELECT " + CASE_COLUMNS + " FROM insurance_case WHERE id = ?", caseId);
    }

    @Override
    public Optional<InsuranceCase> findCaseByRequest(UUID createdByUserId, UUID clientRequestId) {
        return oneCase(
                "SELECT " + CASE_COLUMNS
                        + " FROM insurance_case WHERE created_by_user_id = ? AND client_request_id = ?",
                createdByUserId,
                clientRequestId);
    }

    @Override
    public void saveAccess(InsuranceEvidenceAccess access) {
        jdbcTemplate.update(
                """
            INSERT INTO insurance_evidence_access_audit (
                id, case_id, shipment_id, actor_user_id, purpose, outcome,
                reason, correlation_id, occurred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                access.id(),
                access.caseId(),
                access.shipmentId(),
                access.actorUserId(),
                access.purpose() == null ? null : access.purpose().name(),
                access.outcome().name(),
                access.reason(),
                access.correlationId(),
                time(access.occurredAt()));
    }

    @Override
    public boolean saveDecision(InsuranceDecision decision) {
        return jdbcTemplate.update(
                        """
            INSERT INTO insurance_case_decision (
                id, case_id, command_id, input_fingerprint, outcome,
                note, decided_by_user_id, decided_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        decision.id(),
                        decision.caseId(),
                        decision.commandId(),
                        decision.inputFingerprint(),
                        decision.outcome().name(),
                        decision.note(),
                        decision.decidedByUserId(),
                        time(decision.decidedAt()))
                == 1;
    }

    @Override
    public Optional<InsuranceDecision> findDecisionByCommand(UUID commandId) {
        return oneDecision(
                "SELECT " + DECISION_COLUMNS + " FROM insurance_case_decision WHERE command_id = ?", commandId);
    }

    @Override
    public List<InsuranceDecision> findDecisions(UUID caseId) {
        return jdbcTemplate.query(
                "SELECT " + DECISION_COLUMNS
                        + " FROM insurance_case_decision WHERE case_id = ? ORDER BY decided_at, id",
                this::mapDecision,
                caseId);
    }

    private Optional<InsuranceCase> oneCase(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapCase, parameters).stream().findFirst();
    }

    private Optional<InsuranceDecision> oneDecision(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapDecision, parameters).stream().findFirst();
    }

    private InsuranceCase mapCase(ResultSet resultSet, int rowNumber) throws SQLException {
        return new InsuranceCase(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getString("input_fingerprint"),
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                InsurancePurpose.valueOf(resultSet.getString("purpose")),
                resultSet.getObject("assigned_insurer_user_id", UUID.class),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private InsuranceDecision mapDecision(ResultSet resultSet, int rowNumber) throws SQLException {
        return new InsuranceDecision(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("case_id", UUID.class),
                resultSet.getObject("command_id", UUID.class),
                resultSet.getString("input_fingerprint"),
                InsuranceDecisionOutcome.valueOf(resultSet.getString("outcome")),
                resultSet.getString("note"),
                resultSet.getObject("decided_by_user_id", UUID.class),
                instant(resultSet, "decided_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
