package za.co.trademesh.modules.shipment.application;

import java.util.Optional;
import java.util.UUID;

/** Resolves the public business whose trust history a shipment contributes to. */
public interface ShipmentTrustCatalog {

    Optional<UUID> findRequestingBusinessId(UUID shipmentId);
}
