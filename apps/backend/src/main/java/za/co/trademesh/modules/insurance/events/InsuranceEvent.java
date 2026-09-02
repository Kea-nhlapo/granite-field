package za.co.trademesh.modules.insurance.events;

import java.util.UUID;
import za.co.trademesh.modules.insurance.domain.InsuranceDecisionOutcome;
import za.co.trademesh.modules.insurance.domain.InsurancePurpose;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface InsuranceEvent extends DomainEvent
        permits InsuranceEvent.CaseCreated, InsuranceEvent.EvidenceViewed, InsuranceEvent.DecisionRecorded {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record CaseCreated(UUID caseId, UUID shipmentId, InsurancePurpose purpose, UUID assignedInsurerUserId)
            implements InsuranceEvent {
        @Override
        public String type() {
            return "INSURANCE_CASE_CREATED";
        }
    }

    record EvidenceViewed(UUID caseId, UUID shipmentId, InsurancePurpose purpose) implements InsuranceEvent {
        @Override
        public String type() {
            return "INSURANCE_EVIDENCE_VIEWED";
        }
    }

    record DecisionRecorded(UUID caseId, UUID shipmentId, InsuranceDecisionOutcome outcome) implements InsuranceEvent {
        @Override
        public String type() {
            return "INSURANCE_DEMO_DECISION_RECORDED";
        }
    }
}
