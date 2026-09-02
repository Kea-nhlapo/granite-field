package za.co.trademesh.modules.shipment.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;

@Service
class ShipmentAccessService implements ShipmentAccessCatalog {

    private final ShipmentRepository shipments;

    ShipmentAccessService(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentAccess> findOwned(UUID businessId, UUID shipmentId) {
        return shipments
                .findByParticipantBusinessId(businessId, shipmentId)
                .map(shipment -> new ShipmentAccess(
                        shipment.id(),
                        shipment.status() != ShipmentStatus.DELIVERED
                                && shipment.status() != ShipmentStatus.DISPUTED
                                && shipment.status() != ShipmentStatus.CANCELLED));
    }
}
