package za.co.trademesh.modules.procurement.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.procurement.domain.ConfirmedOrder;
import za.co.trademesh.modules.procurement.domain.ProcurementRepository;

@Service
class ShipmentOrderService implements ShipmentOrderCatalog, DeliveryOrderQuantityCatalog {

    private final ProcurementRepository procurement;

    ShipmentOrderService(ProcurementRepository procurement) {
        this.procurement = procurement;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSnapshot> find(UUID buyerBusinessId, UUID orderId) {
        return procurement.findOrder(buyerBusinessId, orderId).map(ShipmentOrderService::snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExpectedQuantity> findExpectedQuantity(UUID buyerBusinessId, UUID orderId) {
        return procurement.findOrder(buyerBusinessId, orderId).flatMap(order -> {
            var units = order.items().stream()
                    .map(item -> item.unitOfMeasure().name())
                    .distinct()
                    .toList();
            if (units.size() != 1) {
                return Optional.empty();
            }
            var quantity = order.items().stream()
                    .map(item -> item.quantity())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            return quantity.signum() > 0
                    ? Optional.of(new ExpectedQuantity(quantity, units.getFirst()))
                    : Optional.empty();
        });
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
