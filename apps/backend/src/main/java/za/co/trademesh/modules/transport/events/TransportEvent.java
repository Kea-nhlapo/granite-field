package za.co.trademesh.modules.transport.events;

import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface TransportEvent extends DomainEvent
        permits TransportEvent.TransporterRegistered,
                TransportEvent.DriverAssigned,
                TransportEvent.CapacityOfferPublished,
                TransportEvent.CapacityOfferCancelled {

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
}
