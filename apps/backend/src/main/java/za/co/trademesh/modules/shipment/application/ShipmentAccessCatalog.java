package za.co.trademesh.modules.shipment.application;

import java.util.Optional;
import java.util.UUID;

/** Narrow shipment boundary used by tracking without exposing shipment internals. */
public interface ShipmentAccessCatalog {

    Optional<ShipmentAccess> findOwned(UUID businessId, UUID shipmentId);

    record ShipmentAccess(UUID shipmentId, boolean acceptsTelemetry) {}
}
