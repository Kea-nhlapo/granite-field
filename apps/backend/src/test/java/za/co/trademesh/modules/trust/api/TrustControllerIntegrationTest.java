package za.co.trademesh.modules.trust.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class TrustControllerIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboarding;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DomainEvents domainEvents;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void keepsThePublicSummarySmallWhileProtectingInternalRecalculation() throws Exception {
        Account owner = register("trust-owner@example.com");
        var started = onboarding.start("2026/230001/07", owner.userId());
        UUID businessId = onboarding.confirm(started.id(), owner.userId()).id();

        mockMvc.perform(get("/api/users/{userId}/trust", owner.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner.userId().toString()))
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.provisionalScore").value(65.0))
                .andExpect(jsonPath("$.verifiedScore").isNumber())
                .andExpect(jsonPath("$.verifiedScheduleMode").value("COMPRESSED_DEMO"))
                .andExpect(jsonPath("$.evidence").doesNotExist())
                .andExpect(jsonPath("$.riskSignals").doesNotExist());

        UUID paymentShipmentId = UUID.randomUUID();
        appendEvidence(
                "SHIPMENT_CREATED",
                paymentShipmentId,
                paymentShipmentId,
                "{\"requestedByBusinessId\":\"" + businessId + "\"}",
                1);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> domainEvents.publish(
                        new PaymentEvent.Locked(
                                UUID.randomUUID(), paymentShipmentId, businessId, new BigDecimal("8500.00"), "ZAR"),
                        owner.userId().toString()));
        mockMvc.perform(get("/api/users/{userId}/trust", owner.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provisionalScore").value(69.0));

        Account otherOwner = register("other-trust-owner@example.com");
        mockMvc.perform(get("/api/users/{userId}/trust", owner.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/public/businesses/{businessId}/trust", businessId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedBadges[0]").value("CIPC_VERIFIED"))
                .andExpect(jsonPath("$.completedTransactionCount").value(0))
                .andExpect(jsonPath("$.deliverySuccessRate").isEmpty())
                .andExpect(jsonPath("$.rating.status").value("NOT_YET_RATED"))
                .andExpect(jsonPath("$.historyBand").value("NO_COMPLETED_HISTORY"))
                .andExpect(jsonPath("$.calculationVersion").value("public-trust/v1"))
                .andExpect(jsonPath("$.legalName").doesNotExist())
                .andExpect(jsonPath("$.registrationNumber").doesNotExist())
                .andExpect(jsonPath("$.successfulDeliveryCount").doesNotExist())
                .andExpect(jsonPath("$.sourceEvidenceThroughSequence").doesNotExist())
                .andExpect(jsonPath("$.riskScore").doesNotExist())
                .andExpect(jsonPath("$.claims").doesNotExist())
                .andExpect(jsonPath("$.deviceSignals").doesNotExist())
                .andExpect(jsonPath("$.investigationNotes").doesNotExist());

        mockMvc.perform(post("/api/internal/trust/businesses/{businessId}/recalculation", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isForbidden());

        Account analyst = internalRiskAnalyst("trust-analyst@example.com");
        UUID shipmentId = UUID.randomUUID();
        appendShipmentEvidence(businessId, shipmentId, "DELIVERED", 1);
        mockMvc.perform(post("/api/internal/trust/businesses/{businessId}/recalculation", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicSummary.completedTransactionCount").value(1))
                .andExpect(jsonPath("$.publicSummary.deliverySuccessRate").value(1.0))
                .andExpect(jsonPath("$.successfulDeliveryCount").value(1))
                .andExpect(jsonPath("$.sourceEvidenceThroughSequence").isNumber());

        appendStatusEvidence(shipmentId, "DISPUTED", 2);
        mockMvc.perform(post("/api/internal/trust/businesses/{businessId}/recalculation", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicSummary.completedTransactionCount").value(1))
                .andExpect(jsonPath("$.publicSummary.deliverySuccessRate").value(0.0));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM trust_public_summary WHERE business_id = ?", Integer.class, businessId))
                .isOne();
    }

    private Account register(String email) {
        var tokens = authService.register(email, PASSWORD, RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private Account internalRiskAnalyst(String email) {
        Account account = register(email);
        jdbcTemplate.update(
                "INSERT INTO access_user_role (user_id, role) VALUES (?, 'INTERNAL_RISK_ANALYST')", account.userId());
        var tokens = authService.login(email, PASSWORD);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private void appendShipmentEvidence(UUID businessId, UUID shipmentId, String status, int minute) {
        appendEvidence(
                "SHIPMENT_CREATED",
                shipmentId,
                shipmentId,
                "{\"requestedByBusinessId\":\"" + businessId + "\"}",
                minute);
        appendStatusEvidence(shipmentId, status, minute + 1);
    }

    private void appendStatusEvidence(UUID shipmentId, String status, int minute) {
        appendEvidence("SHIPMENT_STATUS_CHANGED", shipmentId, shipmentId, "{\"toStatus\":\"" + status + "\"}", minute);
    }

    private void appendEvidence(String type, UUID subjectId, UUID shipmentId, String metadata, int minute) {
        OffsetDateTime occurredAt = OffsetDateTime.ofInstant(
                Instant.parse("2026-09-03T10:00:00Z").plusSeconds(minute * 60L), ZoneOffset.UTC);
        jdbcTemplate.update(
                """
                INSERT INTO evidence_record (
                    id, event_id, evidence_type, subject_type, subject_id, shipment_id,
                    occurred_at, actor, source, correlation_id, schema_version, metadata,
                    payload_checksum, recorded_at
                ) VALUES (?, ?, ?, 'SHIPMENT', ?, ?, ?, NULL, 'trust-test', ?, 1, ?::jsonb, ?, ?)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                type,
                subjectId,
                shipmentId,
                occurredAt,
                UUID.randomUUID(),
                metadata,
                "a".repeat(64),
                occurredAt);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}
}
