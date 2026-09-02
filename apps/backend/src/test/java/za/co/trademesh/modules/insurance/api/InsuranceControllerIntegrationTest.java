package za.co.trademesh.modules.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class InsuranceControllerIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exposesEvidenceOnlyToTheInsurerAssignedToThePurposeScopedCaseAndAuditsEveryView() throws Exception {
        Account ordinary = register("insurance-ordinary@example.com");
        Account assigned = insurer("insurance-assigned@example.com");
        Account otherInsurer = insurer("insurance-other@example.com");
        UUID caseId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        insertCase(caseId, shipmentId, assigned.userId());

        mockMvc.perform(get("/api/insurance/cases/{caseId}/evidence", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ordinary)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSURANCE_EVIDENCE_ACCESS_DENIED"));
        mockMvc.perform(get("/api/insurance/cases/{caseId}/evidence", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherInsurer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSURANCE_EVIDENCE_ACCESS_DENIED"));
        mockMvc.perform(get("/api/insurance/cases/{caseId}/evidence", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(assigned)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insuranceCase.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.insuranceCase.purpose").value("CLAIM_REVIEW"))
                .andExpect(jsonPath("$.insuranceCase.inputFingerprint").doesNotExist())
                .andExpect(jsonPath("$.shipment").isEmpty())
                .andExpect(jsonPath("$.missingEvidence[0]").value("ACTUAL_ROUTE"))
                .andExpect(jsonPath("$.missingEvidence[5]").value("SHIPMENT"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM insurance_evidence_access_audit WHERE case_id = ?",
                        Integer.class,
                        caseId))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT outcome FROM insurance_evidence_access_audit WHERE case_id = ? ORDER BY occurred_at, id",
                        String.class,
                        caseId))
                .containsExactlyInAnyOrder("DENIED", "DENIED", "GRANTED");

        UUID commandId = UUID.randomUUID();
        mockMvc.perform(post("/api/insurance/cases/{caseId}/decisions", caseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(assigned))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "commandId":"%s",
                              "outcome":"NEEDS_MORE_EVIDENCE",
                              "note":"Delivery photo is missing."
                            }
                            """.formatted(commandId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("NEEDS_MORE_EVIDENCE"))
                .andExpect(jsonPath("$.inputFingerprint").doesNotExist())
                .andExpect(jsonPath("$.underwritingDecision").doesNotExist());
    }

    private Account register(String email) {
        var tokens = authService.register(email, PASSWORD, RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private Account insurer(String email) {
        Account account = register(email);
        jdbcTemplate.update("INSERT INTO access_user_role (user_id, role) VALUES (?, 'INSURER')", account.userId());
        var tokens = authService.login(email, PASSWORD);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private void insertCase(UUID caseId, UUID shipmentId, UUID assignedInsurerUserId) {
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
                shipmentId,
                UUID.randomUUID(),
                assignedInsurerUserId,
                UUID.randomUUID(),
                OffsetDateTime.of(2026, 9, 3, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}
}
