package za.co.trademesh.modules.procurement.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.procurement.domain.ConfirmedOrder;
import za.co.trademesh.modules.procurement.domain.ProcurementRepository;

@Service
class ShipmentOrderService implements ShipmentOrderCatalog {

    private final ProcurementRepository procurement;

    ShipmentOrderService(ProcurementRepository procurement) {
        this.procurement = procurement;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSnapshot> find(UUID buyerBusinessId, UUID orderId) {
        return procurement.findOrder(buyerBusinessId, orderId).map(ShipmentOrderService::snapshot);
    }

    private static OrderSnapshot snapshot(ConfirmedOrder order) {
        return new OrderSnapshot(
                order.id(),
                order.buyerBusinessId(),
                order.supplierProfileId(),
                order.sourceDocumentId(),
                order.currency(),
                order.subtotal(),
                order.taxAmount(),
                order.total(),
                order.items().stream()
                        .map(item -> new LineItem(
                                item.productCode(),
                                item.description(),
                                item.quantity(),
                                item.unitOfMeasure().name(),
                                item.unitPrice(),
                                item.lineTotal()))
                        .toList(),
                order.confirmedAt());
    }
}
