package za.co.trademesh.modules.insurance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.document.application.SourceDocumentCatalog;
import za.co.trademesh.modules.evidence.application.ShipmentEvidenceCatalog;
import za.co.trademesh.modules.handover.application.ShipmentHandoverEvidenceCatalog;
import za.co.trademesh.modules.insurance.domain.InsuranceAccessOutcome;
import za.co.trademesh.modules.insurance.domain.InsuranceCase;
import za.co.trademesh.modules.insurance.domain.InsuranceDecision;
import za.co.trademesh.modules.insurance.domain.InsuranceDecisionOutcome;
import za.co.trademesh.modules.insurance.domain.InsuranceEvidenceAccess;
import za.co.trademesh.modules.insurance.domain.InsurancePurpose;
import za.co.trademesh.modules.insurance.domain.InsuranceRepository;
import za.co.trademesh.modules.procurement.application.ShipmentOrderCatalog;
import za.co.trademesh.modules.risk.application.ShipmentRiskEvidenceCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentInsuranceCatalog;
import za.co.trademesh.modules.telemetry.application.ShipmentTelemetryCatalog;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.events.EventProperties;
import za.co.trademesh.shared.security.AccountRole;

class InsuranceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T08:00:00Z");
    private static final UUID SHIPMENT_ID = UUID.fromString("24000000-0000-0000-0000-000000000001");
    private static final UUID BUSINESS_ID = UUID.fromString("24000000-0000-0000-0000-000000000002");
    private static final UUID INSURER_ID = UUID.fromString("24000000-0000-0000-0000-000000000003");
    private static final UUID ANALYST_ID = UUID.fromString("24000000-0000-0000-0000-000000000004");

    private FakeInsuranceRepository repository;
    private Set<UUID> eligibleInsurers;
    private InsuranceService service;

    @BeforeEach
    void setUp() {
        repository = new FakeInsuranceRepository();
        eligibleInsurers = new java.util.HashSet<>(Set.of(INSURER_ID));
        ShipmentInsuranceCatalog shipments =
                shipmentId -> SHIPMENT_ID.equals(shipmentId) ? Optional.of(shipment()) : Optional.empty();
        ShipmentOrderCatalog orders = (businessId, orderId) -> Optional.empty();
        SourceDocumentCatalog documents = documentId -> Optional.empty();
        ShipmentTelemetryCatalog telemetry = shipmentId -> new ShipmentTelemetryCatalog.ActualRoute(List.of(), false);
        ShipmentHandoverEvidenceCatalog handovers = shipmentId -> List.of();
        ShipmentRiskEvidenceCatalog risks = shipmentId -> List.of();
        ShipmentEvidenceCatalog evidence =
                shipmentId -> new ShipmentEvidenceCatalog.ShipmentEvidencePackage(shipmentId, List.of());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new InsuranceService(
                repository,
                (userId, role) -> role == AccountRole.INSURER && eligibleInsurers.contains(userId),
                shipments,
                orders,
                documents,
                telemetry,
                handovers,
                risks,
                evidence,
                new DomainEvents(ignored -> {}, clock, new EventProperties("insurance-test")),
                clock);
    }

    @Test
    void createsOnePurposeScopedCaseForAnEligibleInsurer() {
        UUID requestId = UUID.randomUUID();
        var command =
                new InsuranceService.CreateCase(requestId, SHIPMENT_ID, InsurancePurpose.CLAIM_REVIEW, INSURER_ID);

        InsuranceCase created = service.createCase(command, ANALYST_ID);
        InsuranceCase replayed = service.createCase(command, ANALYST_ID);

        assertThat(replayed).isEqualTo(created);
        assertThat(repository.cases).hasSize(1);
        assertThat(created.businessId()).isEqualTo(BUSINESS_ID);
        assertThat(created.assignedInsurerUserId()).isEqualTo(INSURER_ID);
        assertThat(created.purpose()).isEqualTo(InsurancePurpose.CLAIM_REVIEW);
        assertThat(created.inputFingerprint()).hasSize(64);
    }

    @Test
    void rejectsAnIneligibleAssigneeAndConflictingRequestReplay() {
        UUID inactiveInsurer = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createCase(
                        new InsuranceService.CreateCase(
                                requestId, SHIPMENT_ID, InsurancePurpose.CLAIM_REVIEW, inactiveInsurer),
                        ANALYST_ID))
                .isInstanceOf(InsuranceException.class)
                .extracting(error -> ((InsuranceException) error).code())
                .isEqualTo("INSURER_NOT_ELIGIBLE");

        service.createCase(
                new InsuranceService.CreateCase(requestId, SHIPMENT_ID, InsurancePurpose.CLAIM_REVIEW, INSURER_ID),
                ANALYST_ID);
        assertThatThrownBy(() -> service.createCase(
                        new InsuranceService.CreateCase(
                                requestId, SHIPMENT_ID, InsurancePurpose.LOSS_INVESTIGATION, INSURER_ID),
                        ANALYST_ID))
                .isInstanceOf(InsuranceException.class)
                .extracting(error -> ((InsuranceException) error).code())
                .isEqualTo("INSURANCE_REQUEST_CONFLICT");
    }

    @Test
    void auditsDeniedViewsForWrongRoleWrongAssignmentAndUnknownCase() {
        InsuranceCase insuranceCase = createCase();
        UUID ordinaryUser = UUID.randomUUID();
        UUID otherInsurer = UUID.randomUUID();
        eligibleInsurers.add(otherInsurer);

        assertThatThrownBy(() -> service.viewEvidence(insuranceCase.id(), ordinaryUser))
                .isInstanceOf(InsuranceException.class);
        assertThatThrownBy(() -> service.viewEvidence(insuranceCase.id(), otherInsurer))
                .isInstanceOf(InsuranceException.class);
        assertThatThrownBy(() -> service.viewEvidence(UUID.randomUUID(), otherInsurer))
                .isInstanceOf(InsuranceException.class);

        assertThat(repository.accesses)
                .extracting(InsuranceEvidenceAccess::outcome)
                .containsOnly(InsuranceAccessOutcome.DENIED);
        assertThat(repository.accesses)
                .extracting(InsuranceEvidenceAccess::reason)
                .containsExactly("INSURER_ROLE_REQUIRED", "CASE_ASSIGNMENT_REQUIRED", "CASE_NOT_FOUND");
    }

    @Test
    void returnsAReadModelWithExplicitMissingEvidenceAndAuditsTheView() {
        InsuranceCase insuranceCase = createCase();

        InsuranceEvidencePackage result = service.viewEvidence(insuranceCase.id(), INSURER_ID);

        assertThat(result.shipment().shipmentId()).isEqualTo(SHIPMENT_ID);
        assertThat(result.riskIndicators()).isEmpty();
        assertThat(result.missingEvidence())
                .containsExactly("ACTUAL_ROUTE", "APPROVED_ROUTE", "CARGO_ORDERS", "EVIDENCE_TIMELINE", "HANDOVERS");
        assertThat(repository.accesses).singleElement().satisfies(access -> {
            assertThat(access.outcome()).isEqualTo(InsuranceAccessOutcome.GRANTED);
            assertThat(access.purpose()).isEqualTo(InsurancePurpose.CLAIM_REVIEW);
            assertThat(access.shipmentId()).isEqualTo(SHIPMENT_ID);
        });
    }

    @Test
    void recordsOnlyAnExplicitDemoDecisionAndMakesTheCommandIdempotent() {
        InsuranceCase insuranceCase = createCase();
        UUID commandId = UUID.randomUUID();
        var command = new InsuranceService.DecisionCommand(
                commandId, InsuranceDecisionOutcome.NEEDS_MORE_EVIDENCE, "Delivery photo is still missing.");

        InsuranceDecision created = service.recordDecision(insuranceCase.id(), command, INSURER_ID);
        InsuranceDecision replayed = service.recordDecision(insuranceCase.id(), command, INSURER_ID);

        assertThat(replayed).isEqualTo(created);
        assertThat(repository.decisions).hasSize(1);
        assertThat(created.outcome()).isEqualTo(InsuranceDecisionOutcome.NEEDS_MORE_EVIDENCE);
        assertThat(created.note()).isEqualTo("Delivery photo is still missing.");
    }

    private InsuranceCase createCase() {
        return service.createCase(
                new InsuranceService.CreateCase(
                        UUID.randomUUID(), SHIPMENT_ID, InsurancePurpose.CLAIM_REVIEW, INSURER_ID),
                ANALYST_ID);
    }

    private static ShipmentInsuranceCatalog.ShipmentSnapshot shipment() {
        return new ShipmentInsuranceCatalog.ShipmentSnapshot(
                SHIPMENT_ID,
                BUSINESS_ID,
                "IN_TRANSIT",
                new BigDecimal("100"),
                new BigDecimal("10"),
                List.of(),
                List.of(),
                List.of(),
                NOW.minusSeconds(3600),
                NOW);
    }

    private static final class FakeInsuranceRepository implements InsuranceRepository {
        private final Map<UUID, InsuranceCase> cases = new HashMap<>();
        private final List<InsuranceEvidenceAccess> accesses = new ArrayList<>();
        private final Map<UUID, InsuranceDecision> decisions = new HashMap<>();

        @Override
        public boolean saveCase(InsuranceCase insuranceCase) {
            cases.put(insuranceCase.id(), insuranceCase);
            return true;
        }

        @Override
        public Optional<InsuranceCase> findCase(UUID caseId) {
            return Optional.ofNullable(cases.get(caseId));
        }

        @Override
        public Optional<InsuranceCase> findCaseByRequest(UUID createdByUserId, UUID clientRequestId) {
            return cases.values().stream()
                    .filter(value -> value.createdByUserId().equals(createdByUserId)
                            && value.clientRequestId().equals(clientRequestId))
                    .findFirst();
        }

        @Override
        public void saveAccess(InsuranceEvidenceAccess access) {
            accesses.add(access);
        }

        @Override
        public boolean saveDecision(InsuranceDecision decision) {
            decisions.put(decision.commandId(), decision);
            return true;
        }

        @Override
        public Optional<InsuranceDecision> findDecisionByCommand(UUID commandId) {
            return Optional.ofNullable(decisions.get(commandId));
        }

        @Override
        public List<InsuranceDecision> findDecisions(UUID caseId) {
            return decisions.values().stream()
                    .filter(value -> value.caseId().equals(caseId))
                    .toList();
        }
    }
}
