package za.co.trademesh.modules.handover.events;

import java.math.BigDecimal;
import java.util.UUID;
import za.co.trademesh.modules.handover.domain.HandoverParty;
import za.co.trademesh.modules.handover.domain.HandoverState;
import za.co.trademesh.modules.handover.domain.HandoverType;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface HandoverEvent extends DomainEvent
        permits HandoverEvent.ChallengeIssued,
                HandoverEvent.ConfirmationAccepted,
                HandoverEvent.HandoverFinalized,
                HandoverEvent.DisputeResolved {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record ChallengeIssued(UUID challengeId, UUID shipmentId, HandoverType handoverType) implements HandoverEvent {
        @Override
        public String type() {
            return "HANDOVER_CHALLENGE_ISSUED";
        }
    }

    record ConfirmationAccepted(UUID challengeId, UUID shipmentId, HandoverParty party) implements HandoverEvent {
        @Override
        public String type() {
            return "HANDOVER_CONFIRMATION_ACCEPTED";
        }
    }

    record HandoverFinalized(UUID challengeId, UUID shipmentId, HandoverType handoverType, HandoverState outcome)
            implements HandoverEvent {
        @Override
        public String type() {
            return "HANDOVER_FINALIZED";
        }
    }

    record DisputeResolved(UUID resolutionId, UUID shipmentId, UUID businessId, BigDecimal resolvedAmount)
            implements HandoverEvent {
        @Override
        public String type() {
            return "DELIVERY_DISPUTE_RESOLVED";
        }
    }
}
