package za.co.trademesh.modules.routing.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.routing.events.RoutingEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class RoutingEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof RoutingEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((RoutingEvent) event) {
            case RoutingEvent.RouteCandidatesCalculated calculated ->
                new EvidenceProjection(
                        "ROUTE_CALCULATION",
                        calculated.calculationId(),
                        null,
                        EvidenceMetadata.of(
                                "requestedByBusinessId",
                                calculated.requestedByBusinessId(),
                                "recalculationOfId",
                                calculated.recalculationOfId(),
                                "providerName",
                                calculated.providerName(),
                                "providerVersion",
                                calculated.providerVersion(),
                                "fallbackUsed",
                                calculated.fallbackUsed(),
                                "candidateCount",
                                calculated.candidateCount()));
            case RoutingEvent.RouteChoicesScored scored ->
                new EvidenceProjection(
                        "ROUTE_ASSESSMENT",
                        scored.assessmentId(),
                        null,
                        EvidenceMetadata.of(
                                "calculationId",
                                scored.calculationId(),
                                "requestedByBusinessId",
                                scored.requestedByBusinessId(),
                                "cargoProfile",
                                scored.cargoProfile(),
                                "algorithmVersion",
                                scored.algorithmVersion(),
                                "recommendedCandidateId",
                                scored.recommendedCandidateId(),
                                "totalScore",
                                scored.totalScore(),
                                "confidence",
                                scored.confidence()));
        };
    }
}
