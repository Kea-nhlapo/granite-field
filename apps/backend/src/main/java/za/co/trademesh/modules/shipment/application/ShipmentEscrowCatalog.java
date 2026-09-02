package za.co.trademesh.modules.shipment.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Shipment facts needed by the payment module, scoped to one paying business. */
public interface ShipmentEscrowCatalog {

    Optional<ShipmentEscrow> find(UUID businessId, UUID shipmentId);

    record ShipmentEscrow(
            UUID shipmentId, UUID businessId, List<UUID> orderIds, boolean releaseAllowed, boolean disputed) {
        public ShipmentEscrow {
            orderIds = List.copyOf(orderIds);
        }
    }
}
