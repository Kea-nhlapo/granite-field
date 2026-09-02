package za.co.trademesh.modules.routing.events;

import java.math.BigDecimal;
import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface RoutingEvent extends DomainEvent
        permits RoutingEvent.RouteCandidatesCalculated, RoutingEvent.RouteChoicesScored {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record RouteCandidatesCalculated(
            UUID calculationId,
            UUID requestedByBusinessId,
            UUID recalculationOfId,
            String providerName,
            String providerVersion,
            boolean fallbackUsed,
            int candidateCount)
            implements RoutingEvent {
        @Override
        public String type() {
            return "ROUTE_CANDIDATES_CALCULATED";
        }
    }

    record RouteChoicesScored(
            UUID assessmentId,
            UUID calculationId,
            UUID requestedByBusinessId,
            String cargoProfile,
            String algorithmVersion,
            UUID recommendedCandidateId,
            BigDecimal totalScore,
            BigDecimal confidence)
            implements RoutingEvent {
        @Override
        public String type() {
            return "ROUTE_CHOICES_SCORED";
        }
    }
}
