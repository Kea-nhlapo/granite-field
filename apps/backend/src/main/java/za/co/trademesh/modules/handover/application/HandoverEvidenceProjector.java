package za.co.trademesh.modules.handover.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.handover.events.HandoverEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class HandoverEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof HandoverEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((HandoverEvent) event) {
            case HandoverEvent.ChallengeIssued issued ->
                new EvidenceProjection(
                        "HANDOVER",
                        issued.challengeId(),
                        issued.shipmentId(),
                        EvidenceMetadata.of("handoverType", issued.handoverType()));
            case HandoverEvent.ConfirmationAccepted accepted ->
                new EvidenceProjection(
                        "HANDOVER",
                        accepted.challengeId(),
                        accepted.shipmentId(),
                        EvidenceMetadata.of("party", accepted.party()));
            case HandoverEvent.HandoverFinalized finalized ->
                new EvidenceProjection(
                        "HANDOVER",
                        finalized.challengeId(),
                        finalized.shipmentId(),
                        EvidenceMetadata.of("handoverType", finalized.handoverType(), "outcome", finalized.outcome()));
            case HandoverEvent.DisputeResolved resolved ->
                new EvidenceProjection(
                        "HANDOVER_RESOLUTION",
                        resolved.resolutionId(),
                        resolved.shipmentId(),
                        EvidenceMetadata.of(
                                "businessId", resolved.businessId(), "resolvedAmount", resolved.resolvedAmount()));
        };
    }
}
