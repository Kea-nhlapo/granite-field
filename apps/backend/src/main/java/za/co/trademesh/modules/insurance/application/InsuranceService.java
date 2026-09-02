package za.co.trademesh.modules.insurance.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.application.AccountRoleDirectory;
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
import za.co.trademesh.modules.insurance.events.InsuranceEvent;
import za.co.trademesh.modules.procurement.application.ShipmentOrderCatalog;
import za.co.trademesh.modules.risk.application.ShipmentRiskEvidenceCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentInsuranceCatalog;
import za.co.trademesh.modules.telemetry.application.ShipmentTelemetryCatalog;
import za.co.trademesh.shared.events.CorrelationContext;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.security.AccountRole;

@Service
public class InsuranceService {

    private static final int MAX_NOTE_LENGTH = 1_000;

    private final InsuranceRepository insurance;
    private final AccountRoleDirectory accounts;
    private final ShipmentInsuranceCatalog shipments;
    private final ShipmentOrderCatalog orders;
    private final SourceDocumentCatalog documents;
    private final ShipmentTelemetryCatalog telemetry;
    private final ShipmentHandoverEvidenceCatalog handovers;
    private final ShipmentRiskEvidenceCatalog risks;
    private final ShipmentEvidenceCatalog evidence;
    private final DomainEvents events;
    private final Clock clock;

    public InsuranceService(
            InsuranceRepository insurance,
            AccountRoleDirectory accounts,
            ShipmentInsuranceCatalog shipments,
            ShipmentOrderCatalog orders,
            SourceDocumentCatalog documents,
            ShipmentTelemetryCatalog telemetry,
            ShipmentHandoverEvidenceCatalog handovers,
            ShipmentRiskEvidenceCatalog risks,
            ShipmentEvidenceCatalog evidence,
            DomainEvents events,
            Clock clock) {
        this.insurance = insurance;
        this.accounts = accounts;
        this.shipments = shipments;
        this.orders = orders;
        this.documents = documents;
        this.telemetry = telemetry;
        this.handovers = handovers;
        this.risks = risks;
        this.evidence = evidence;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public InsuranceCase createCase(CreateCase command, UUID actorUserId) {
        CreateCase normalized = normalize(command);
        UUID actor = requiredId(actorUserId);
        String inputFingerprint =
                fingerprint(normalized.shipmentId(), normalized.purpose(), normalized.assignedInsurerUserId());
        var prior = insurance.findCaseByRequest(actor, normalized.clientRequestId());
        if (prior.isPresent()) {
            if (!prior.get().inputFingerprint().equals(inputFingerprint)) {
                throw InsuranceException.requestConflict();
            }
            return prior.get();
        }
        if (!accounts.isActiveWithRole(normalized.assignedInsurerUserId(), AccountRole.INSURER)) {
            throw InsuranceException.insurerNotEligible();
        }
        var shipment = shipments.find(normalized.shipmentId()).orElseThrow(InsuranceException::shipmentNotFound);
        InsuranceCase insuranceCase = new InsuranceCase(
                UUID.randomUUID(),
                normalized.clientRequestId(),
                inputFingerprint,
                shipment.shipmentId(),
                shipment.businessId(),
                normalized.purpose(),
                normalized.assignedInsurerUserId(),
                actor,
                now());
        if (!insurance.saveCase(insuranceCase)) {
            return insurance
                    .findCaseByRequest(actor, normalized.clientRequestId())
                    .filter(existing -> existing.inputFingerprint().equals(inputFingerprint))
                    .orElseThrow(InsuranceException::requestConflict);
        }
        events.publish(
                new InsuranceEvent.CaseCreated(
                        insuranceCase.id(),
                        insuranceCase.shipmentId(),
                        insuranceCase.purpose(),
                        insuranceCase.assignedInsurerUserId()),
                actor.toString());
        return insuranceCase;
    }

    @Transactional(noRollbackFor = InsuranceException.class)
    public InsuranceEvidencePackage viewEvidence(UUID caseId, UUID actorUserId) {
        UUID requestedCaseId = requiredId(caseId);
        UUID actor = requiredId(actorUserId);
        InsuranceCase insuranceCase = insurance.findCase(requestedCaseId).orElse(null);
        if (insuranceCase == null) {
            audit(requestedCaseId, null, actor, null, InsuranceAccessOutcome.DENIED, "CASE_NOT_FOUND");
            throw InsuranceException.caseNotFound();
        }
        if (!accounts.isActiveWithRole(actor, AccountRole.INSURER)) {
            audit(
                    insuranceCase.id(),
                    insuranceCase.shipmentId(),
                    actor,
                    insuranceCase.purpose(),
                    InsuranceAccessOutcome.DENIED,
                    "INSURER_ROLE_REQUIRED");
            throw InsuranceException.evidenceAccessDenied();
        }
        if (!insuranceCase.assignedInsurerUserId().equals(actor)) {
            audit(
                    insuranceCase.id(),
                    insuranceCase.shipmentId(),
                    actor,
                    insuranceCase.purpose(),
                    InsuranceAccessOutcome.DENIED,
                    "CASE_ASSIGNMENT_REQUIRED");
            throw InsuranceException.evidenceAccessDenied();
        }

        ShipmentInsuranceCatalog.ShipmentSnapshot shipment =
                shipments.find(insuranceCase.shipmentId()).orElse(null);
        if (shipment != null && !shipment.businessId().equals(insuranceCase.businessId())) {
            audit(
                    insuranceCase.id(),
                    insuranceCase.shipmentId(),
                    actor,
                    insuranceCase.purpose(),
                    InsuranceAccessOutcome.DENIED,
                    "CASE_TENANT_MISMATCH");
            throw InsuranceException.evidenceAccessDenied();
        }

        InsuranceEvidencePackage evidencePackage = assemble(insuranceCase, shipment);
        audit(
                insuranceCase.id(),
                insuranceCase.shipmentId(),
                actor,
                insuranceCase.purpose(),
                InsuranceAccessOutcome.GRANTED,
                "PURPOSE_SCOPED_CASE_ACCESS");
        events.publish(
                new InsuranceEvent.EvidenceViewed(
                        insuranceCase.id(), insuranceCase.shipmentId(), insuranceCase.purpose()),
                actor.toString());
        return evidencePackage;
    }

    @Transactional
    public InsuranceDecision recordDecision(UUID caseId, DecisionCommand command, UUID actorUserId) {
        UUID requestedCaseId = requiredId(caseId);
        UUID actor = requiredId(actorUserId);
        DecisionCommand normalized = normalize(command);
        String inputFingerprint = fingerprint(requestedCaseId, normalized.outcome(), normalized.note());
        var prior = insurance.findDecisionByCommand(normalized.commandId());
        if (prior.isPresent()) {
            if (!prior.get().caseId().equals(requestedCaseId)
                    || !prior.get().inputFingerprint().equals(inputFingerprint)) {
                throw InsuranceException.decisionConflict();
            }
            return prior.get();
        }
        InsuranceCase insuranceCase = requireAssignedCase(requestedCaseId, actor);
        InsuranceDecision decision = new InsuranceDecision(
                UUID.randomUUID(),
                insuranceCase.id(),
                normalized.commandId(),
                inputFingerprint,
                normalized.outcome(),
                normalized.note(),
                actor,
                now());
        if (!insurance.saveDecision(decision)) {
            return insurance
                    .findDecisionByCommand(normalized.commandId())
                    .filter(existing -> existing.caseId().equals(requestedCaseId)
                            && existing.inputFingerprint().equals(inputFingerprint))
                    .orElseThrow(InsuranceException::decisionConflict);
        }
        events.publish(
                new InsuranceEvent.DecisionRecorded(insuranceCase.id(), insuranceCase.shipmentId(), decision.outcome()),
                actor.toString());
        return decision;
    }

    private InsuranceEvidencePackage assemble(
            InsuranceCase insuranceCase, ShipmentInsuranceCatalog.ShipmentSnapshot shipment) {
        TreeSet<String> missing = new TreeSet<>();
        List<ShipmentOrderCatalog.OrderSnapshot> orderSnapshots = new ArrayList<>();
        Map<UUID, SourceDocumentCatalog.SourceDocument> sourceDocuments = new LinkedHashMap<>();
        if (shipment == null) {
            missing.add("SHIPMENT");
            missing.add("CARGO_ORDERS");
            missing.add("APPROVED_ROUTE");
        } else {
            if (shipment.cargoStops().isEmpty()) {
                missing.add("CARGO_ORDERS");
            }
            for (ShipmentInsuranceCatalog.CargoStop stop : shipment.cargoStops()) {
                var order = orders.find(stop.buyerBusinessId(), stop.orderId());
                if (order.isEmpty()) {
                    missing.add("ORDER:" + stop.orderId());
                    continue;
                }
                ShipmentOrderCatalog.OrderSnapshot snapshot = order.get();
                orderSnapshots.add(snapshot);
                var document = documents.find(snapshot.sourceDocumentId());
                if (document.isEmpty()) {
                    missing.add("SOURCE_DOCUMENT:" + snapshot.sourceDocumentId());
                    continue;
                }
                SourceDocumentCatalog.SourceDocument source = document.get();
                sourceDocuments.putIfAbsent(source.documentId(), source);
                if (!"AVAILABLE".equals(source.fileAvailability())) {
                    missing.add("SOURCE_FILE:" + source.fileId() + ":" + source.fileAvailability());
                }
            }
            if (shipment.assignments().isEmpty()
                    || shipment.assignments().stream()
                            .allMatch(value -> value.approvedRoute().isEmpty())) {
                missing.add("APPROVED_ROUTE");
            }
        }

        ShipmentTelemetryCatalog.ActualRoute actualRoute = telemetry.actualRoute(insuranceCase.shipmentId());
        if (actualRoute.points().isEmpty()) {
            missing.add("ACTUAL_ROUTE");
        }
        List<ShipmentHandoverEvidenceCatalog.Handover> handoverSnapshots = handovers.find(insuranceCase.shipmentId());
        if (handoverSnapshots.isEmpty()) {
            missing.add("HANDOVERS");
        }
        var riskSnapshots = risks.find(insuranceCase.shipmentId());
        var timeline = evidence.packageFor(insuranceCase.shipmentId());
        if (timeline.entries().isEmpty()) {
            missing.add("EVIDENCE_TIMELINE");
        }
        return new InsuranceEvidencePackage(
                insuranceCase,
                shipment,
                orderSnapshots,
                List.copyOf(sourceDocuments.values()),
                actualRoute,
                handoverSnapshots,
                riskSnapshots,
                timeline,
                insurance.findDecisions(insuranceCase.id()),
                List.copyOf(missing));
    }

    private InsuranceCase requireAssignedCase(UUID caseId, UUID actor) {
        InsuranceCase insuranceCase = insurance.findCase(caseId).orElseThrow(InsuranceException::caseNotFound);
        if (!accounts.isActiveWithRole(actor, AccountRole.INSURER)
                || !insuranceCase.assignedInsurerUserId().equals(actor)) {
            throw InsuranceException.evidenceAccessDenied();
        }
        return insuranceCase;
    }

    private void audit(
            UUID caseId,
            UUID shipmentId,
            UUID actor,
            InsurancePurpose purpose,
            InsuranceAccessOutcome outcome,
            String reason) {
        insurance.saveAccess(new InsuranceEvidenceAccess(
                UUID.randomUUID(),
                caseId,
                shipmentId,
                actor,
                purpose,
                outcome,
                reason,
                CorrelationContext.correlationId(),
                now()));
    }

    private static CreateCase normalize(CreateCase command) {
        if (command == null || command.purpose() == null) {
            throw InsuranceException.invalidRequest();
        }
        return new CreateCase(
                requiredId(command.clientRequestId()),
                requiredId(command.shipmentId()),
                command.purpose(),
                requiredId(command.assignedInsurerUserId()));
    }

    private static DecisionCommand normalize(DecisionCommand command) {
        if (command == null || command.outcome() == null || command.note() == null) {
            throw InsuranceException.invalidRequest();
        }
        String note = command.note().strip();
        if (note.isEmpty() || note.length() > MAX_NOTE_LENGTH) {
            throw InsuranceException.invalidRequest();
        }
        return new DecisionCommand(requiredId(command.commandId()), command.outcome(), note);
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw InsuranceException.invalidRequest();
        }
        return value;
    }

    private static String fingerprint(Object... values) {
        String input =
                java.util.Arrays.stream(values).map(String::valueOf).collect(java.util.stream.Collectors.joining("|"));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public record CreateCase(
            UUID clientRequestId, UUID shipmentId, InsurancePurpose purpose, UUID assignedInsurerUserId) {}

    public record DecisionCommand(UUID commandId, InsuranceDecisionOutcome outcome, String note) {}
}
