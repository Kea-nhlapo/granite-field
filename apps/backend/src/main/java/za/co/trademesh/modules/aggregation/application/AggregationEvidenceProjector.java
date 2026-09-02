package za.co.trademesh.modules.aggregation.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.aggregation.events.AggregationEvent;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class AggregationEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof AggregationEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        AggregationEvent.SuggestionCreated created = (AggregationEvent.SuggestionCreated) event;
        return new EvidenceProjection(
                "DEMAND_GROUP",
                created.suggestionId(),
                null,
                EvidenceMetadata.of(
                        "requestedByBusinessId",
                        created.requestedByBusinessId(),
                        "anchorOrderId",
                        created.anchorOrderId(),
                        "status",
                        created.status(),
                        "includedOrderCount",
                        created.includedOrderCount()));
    }
}
