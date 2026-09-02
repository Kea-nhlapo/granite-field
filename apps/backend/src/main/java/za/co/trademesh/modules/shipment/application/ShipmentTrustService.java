package za.co.trademesh.modules.shipment.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;

@Service
class ShipmentTrustService implements ShipmentTrustCatalog {

    private final ShipmentRepository shipments;

    ShipmentTrustService(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findRequestingBusinessId(UUID shipmentId) {
        return shipments.findById(shipmentId).map(shipment -> shipment.requestedByBusinessId());
    }
}
