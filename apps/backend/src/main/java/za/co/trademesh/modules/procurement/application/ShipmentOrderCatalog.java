package za.co.trademesh.modules.procurement.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Commercial order details exposed only for orders already linked to a shipment. */
public interface ShipmentOrderCatalog {

    Optional<OrderSnapshot> find(UUID buyerBusinessId, UUID orderId);

    record OrderSnapshot(
            UUID orderId,
            UUID buyerBusinessId,
            UUID supplierProfileId,
            UUID sourceDocumentId,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            List<LineItem> items,
            Instant confirmedAt) {
        public OrderSnapshot {
            items = List.copyOf(items);
        }
    }

    record LineItem(
            String productCode,
            String description,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {}
}
