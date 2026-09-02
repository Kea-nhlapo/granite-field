package za.co.trademesh.modules.insurance.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.trademesh.support.PostgresIntegrationTest;

class InsuranceAuditIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsChangesToAccessAuditsAndDemoDecisions() {
        UUID caseId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        UUID accessId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 3, 8, 0, 0, 0, ZoneOffset.UTC);
        jdbcTemplate.update(
                """
                INSERT INTO insurance_case (
                    id, client_request_id, input_fingerprint, shipment_id, business_id, purpose,
                    assigned_insurer_user_id, created_by_user_id, created_at
                ) VALUES (?, ?, ?, ?, ?, 'CLAIM_REVIEW', ?, ?, ?)
                """,
                caseId,
                UUID.randomUUID(),
                "a".repeat(64),
                UUID.randomUUID(),
                UUID.randomUUID(),
                actor,
                UUID.randomUUID(),
                now);
        jdbcTemplate.update(
                """
                INSERT INTO insurance_evidence_access_audit (
                    id, case_id, shipment_id, actor_user_id, purpose, outcome,
                    reason, correlation_id, occurred_at
                ) VALUES (?, ?, ?, ?, 'CLAIM_REVIEW', 'GRANTED', ?, ?, ?)
                """, accessId, caseId, UUID.randomUUID(), actor, "PURPOSE_SCOPED_CASE_ACCESS", UUID.randomUUID(), now);
        jdbcTemplate.update(
                """
                INSERT INTO insurance_case_decision (
                    id, case_id, command_id, input_fingerprint, outcome,
                    note, decided_by_user_id, decided_at
                ) VALUES (?, ?, ?, ?, 'NEEDS_MORE_EVIDENCE', ?, ?, ?)
                """, decisionId, caseId, UUID.randomUUID(), "b".repeat(64), "Delivery photo is missing.", actor, now);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE insurance_evidence_access_audit SET reason = 'CHANGED' WHERE id = ?", accessId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM insurance_case_decision WHERE id = ?", decisionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
