package za.co.trademesh.modules.transport.events;

import java.time.Instant;
import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface TransportEvent extends DomainEvent
        permits TransportEvent.TransporterRegistered,
                TransportEvent.DriverAssigned,
                TransportEvent.CapacityOfferPublished,
                TransportEvent.CapacityOfferCancelled,
                TransportEvent.CapacityMatchCompleted,
                TransportEvent.CapacityReserved,
                TransportEvent.CapacityReleased {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record TransporterRegistered(UUID transporterId, UUID businessId) implements TransportEvent {
        @Override
        public String type() {
            return "TRANSPORTER_REGISTERED";
        }
    }

    record DriverAssigned(UUID assignmentId, UUID transporterId, UUID vehicleId, UUID driverId)
            implements TransportEvent {
        @Override
        public String type() {
            return "DRIVER_ASSIGNED";
        }
    }

    record CapacityOfferPublished(UUID offerId, UUID transporterId, UUID vehicleId) implements TransportEvent {
        @Override
        public String type() {
            return "CAPACITY_OFFER_PUBLISHED";
        }
    }

    record CapacityOfferCancelled(UUID offerId, UUID transporterId) implements TransportEvent {
        @Override
        public String type() {
            return "CAPACITY_OFFER_CANCELLED";
        }
    }

    record CapacityMatchCompleted(
            UUID matchSearchId, UUID requestedByBusinessId, UUID demandGroupSuggestionId, int compatibleOfferCount)
            implements TransportEvent {
        @Override
        public String type() {
            return "CAPACITY_MATCH_COMPLETED";
        }
    }

    record CapacityReserved(UUID reservationId, UUID matchSearchId, UUID offerId, Instant expiresAt)
            implements TransportEvent {
        @Override
        public String type() {
            return "CAPACITY_RESERVED";
        }
    }

    record CapacityReleased(UUID reservationId, UUID matchSearchId, UUID offerId, boolean expired)
            implements TransportEvent {
        @Override
        public String type() {
            return expired ? "CAPACITY_RESERVATION_EXPIRED" : "CAPACITY_RELEASED";
        }
    }
}
