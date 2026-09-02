package za.co.trademesh.modules.insurance.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.document.application.SourceDocumentCatalog;
import za.co.trademesh.modules.evidence.application.ShipmentEvidenceCatalog;
import za.co.trademesh.modules.handover.application.ShipmentHandoverEvidenceCatalog;
import za.co.trademesh.modules.insurance.application.InsuranceEvidencePackage;
import za.co.trademesh.modules.insurance.application.InsuranceService;
import za.co.trademesh.modules.insurance.domain.InsuranceCase;
import za.co.trademesh.modules.insurance.domain.InsuranceDecision;
import za.co.trademesh.modules.insurance.domain.InsuranceDecisionOutcome;
import za.co.trademesh.modules.insurance.domain.InsurancePurpose;
import za.co.trademesh.modules.procurement.application.ShipmentOrderCatalog;
import za.co.trademesh.modules.risk.application.ShipmentRiskEvidenceCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentInsuranceCatalog;
import za.co.trademesh.modules.telemetry.application.ShipmentTelemetryCatalog;

final class InsuranceContracts {

    private InsuranceContracts() {}

    record CreateCaseRequest(
            @NotNull UUID clientRequestId,
            @NotNull UUID shipmentId,
            @NotNull InsurancePurpose purpose,
            @NotNull UUID assignedInsurerUserId) {

        InsuranceService.CreateCase toCommand() {
            return new InsuranceService.CreateCase(clientRequestId, shipmentId, purpose, assignedInsurerUserId);
        }
    }

    record RecordDecisionRequest(
            @NotNull UUID commandId,
            @NotNull InsuranceDecisionOutcome outcome,
            @NotNull @Size(min = 1, max = 1000) String note) {

        InsuranceService.DecisionCommand toCommand() {
            return new InsuranceService.DecisionCommand(commandId, outcome, note);
        }
    }

    record CaseResponse(
            UUID caseId,
            UUID shipmentId,
            UUID businessId,
            InsurancePurpose purpose,
            UUID assignedInsurerUserId,
            UUID createdByUserId,
            Instant createdAt) {

        static CaseResponse from(InsuranceCase insuranceCase) {
            return new CaseResponse(
                    insuranceCase.id(),
                    insuranceCase.shipmentId(),
                    insuranceCase.businessId(),
                    insuranceCase.purpose(),
                    insuranceCase.assignedInsurerUserId(),
                    insuranceCase.createdByUserId(),
                    insuranceCase.createdAt());
        }
    }

    record DecisionResponse(
            UUID decisionId,
            UUID caseId,
            InsuranceDecisionOutcome outcome,
            String note,
            UUID decidedByUserId,
            Instant decidedAt) {

        static DecisionResponse from(InsuranceDecision decision) {
            return new DecisionResponse(
                    decision.id(),
                    decision.caseId(),
                    decision.outcome(),
                    decision.note(),
                    decision.decidedByUserId(),
                    decision.decidedAt());
        }
    }

    record EvidencePackageResponse(
            CaseResponse insuranceCase,
            ShipmentInsuranceCatalog.ShipmentSnapshot shipment,
            List<ShipmentOrderCatalog.OrderSnapshot> orders,
            List<SourceDocumentCatalog.SourceDocument> sourceDocuments,
            ShipmentTelemetryCatalog.ActualRoute actualRoute,
            List<ShipmentHandoverEvidenceCatalog.Handover> handovers,
            List<ShipmentRiskEvidenceCatalog.RiskIndicator> riskIndicators,
            ShipmentEvidenceCatalog.ShipmentEvidencePackage evidenceTimeline,
            List<DecisionResponse> decisions,
            List<String> missingEvidence) {

        static EvidencePackageResponse from(InsuranceEvidencePackage evidencePackage) {
            return new EvidencePackageResponse(
                    CaseResponse.from(evidencePackage.insuranceCase()),
                    evidencePackage.shipment(),
                    evidencePackage.orders(),
                    evidencePackage.sourceDocuments(),
                    evidencePackage.actualRoute(),
                    evidencePackage.handovers(),
                    evidencePackage.riskIndicators(),
                    evidencePackage.evidenceTimeline(),
                    evidencePackage.decisions().stream()
                            .map(DecisionResponse::from)
                            .toList(),
                    evidencePackage.missingEvidence());
        }
    }
}
