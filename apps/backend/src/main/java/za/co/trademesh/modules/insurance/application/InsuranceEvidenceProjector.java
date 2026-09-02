package za.co.trademesh.modules.insurance.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.insurance.events.InsuranceEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class InsuranceEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof InsuranceEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((InsuranceEvent) event) {
            case InsuranceEvent.CaseCreated created ->
                new EvidenceProjection(
                        "INSURANCE_CASE",
                        created.caseId(),
                        created.shipmentId(),
                        EvidenceMetadata.of(
                                "purpose",
                                created.purpose(),
                                "assignedInsurerUserId",
                                created.assignedInsurerUserId()));
            case InsuranceEvent.EvidenceViewed viewed ->
                new EvidenceProjection(
                        "INSURANCE_CASE",
                        viewed.caseId(),
                        viewed.shipmentId(),
                        EvidenceMetadata.of("purpose", viewed.purpose()));
            case InsuranceEvent.DecisionRecorded recorded ->
                new EvidenceProjection(
                        "INSURANCE_CASE",
                        recorded.caseId(),
                        recorded.shipmentId(),
                        EvidenceMetadata.of("outcome", recorded.outcome()));
        };
    }
}
