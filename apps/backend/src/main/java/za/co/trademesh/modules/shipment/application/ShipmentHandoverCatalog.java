package za.co.trademesh.modules.shipment.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Shipment operations exposed to the handover module without leaking shipment internals. */
public interface ShipmentHandoverCatalog {

    Optional<HandoverShipment> findOwned(UUID businessId, UUID shipmentId);

    void complete(
            UUID businessId,
            UUID shipmentId,
            UUID commandId,
            Completion completion,
            String reason,
            UUID correlationId,
            UUID actorUserId);

    record HandoverShipment(
            UUID shipmentId,
            UUID businessId,
            Stage stage,
            Location collectionLocation,
            List<DeliveryStop> deliveryStops,
            Instant updatedAt) {

        public HandoverShipment {
            deliveryStops = List.copyOf(deliveryStops);
        }
    }

    record DeliveryStop(UUID orderId, UUID buyerBusinessId, Location location) {}

    record Location(String label, double latitude, double longitude) {}

    enum Stage {
        AWAITING_COLLECTION,
        COLLECTED,
        IN_TRANSIT,
        DELAYED,
        TERMINAL
    }

    enum Completion {
        COLLECTION_VERIFIED,
        DELIVERY_VERIFIED,
        DELIVERY_DISPUTED
    }
}
