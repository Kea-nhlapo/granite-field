package za.co.trademesh.modules.insurance.application;

import java.util.List;
import za.co.trademesh.modules.document.application.SourceDocumentCatalog;
import za.co.trademesh.modules.evidence.application.ShipmentEvidenceCatalog;
import za.co.trademesh.modules.handover.application.ShipmentHandoverEvidenceCatalog;
import za.co.trademesh.modules.insurance.domain.InsuranceCase;
import za.co.trademesh.modules.insurance.domain.InsuranceDecision;
import za.co.trademesh.modules.procurement.application.ShipmentOrderCatalog;
import za.co.trademesh.modules.risk.application.ShipmentRiskEvidenceCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentInsuranceCatalog;
import za.co.trademesh.modules.telemetry.application.ShipmentTelemetryCatalog;

public record InsuranceEvidencePackage(
        InsuranceCase insuranceCase,
        ShipmentInsuranceCatalog.ShipmentSnapshot shipment,
        List<ShipmentOrderCatalog.OrderSnapshot> orders,
        List<SourceDocumentCatalog.SourceDocument> sourceDocuments,
        ShipmentTelemetryCatalog.ActualRoute actualRoute,
        List<ShipmentHandoverEvidenceCatalog.Handover> handovers,
        List<ShipmentRiskEvidenceCatalog.RiskIndicator> riskIndicators,
        ShipmentEvidenceCatalog.ShipmentEvidencePackage evidenceTimeline,
        List<InsuranceDecision> decisions,
        List<String> missingEvidence) {

    public InsuranceEvidencePackage {
        orders = List.copyOf(orders);
        sourceDocuments = List.copyOf(sourceDocuments);
        handovers = List.copyOf(handovers);
        riskIndicators = List.copyOf(riskIndicators);
        decisions = List.copyOf(decisions);
        missingEvidence = List.copyOf(missingEvidence);
    }
}
