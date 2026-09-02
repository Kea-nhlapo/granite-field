package za.co.trademesh.modules.shipment.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;

@Service
class ShipmentEscrowService implements ShipmentEscrowCatalog {

    private final ShipmentRepository shipments;

    ShipmentEscrowService(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentEscrow> find(UUID businessId, UUID shipmentId) {
        return shipments.findById(shipmentId).flatMap(shipment -> {
            var orderIds = shipment.loadOrders().stream()
                    .filter(order -> order.buyerBusinessId().equals(businessId))
                    .map(order -> order.orderId())
                    .toList();
            if (orderIds.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ShipmentEscrow(
                    shipment.id(),
                    businessId,
                    orderIds,
                    shipment.status() == ShipmentStatus.DELIVERED,
                    shipment.status() == ShipmentStatus.DISPUTED));
        });
    }
}
