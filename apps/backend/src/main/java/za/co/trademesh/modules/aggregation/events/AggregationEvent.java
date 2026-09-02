package za.co.trademesh.modules.aggregation.events;

import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface AggregationEvent extends DomainEvent permits AggregationEvent.SuggestionCreated {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record SuggestionCreated(
            UUID suggestionId, UUID requestedByBusinessId, UUID anchorOrderId, String status, int includedOrderCount)
            implements AggregationEvent {
        @Override
        public String type() {
            return "DEMAND_GROUP_SUGGESTED";
        }
    }
}
