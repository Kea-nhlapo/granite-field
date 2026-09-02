package za.co.trademesh.modules.procurement.events;

import java.math.BigDecimal;
import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface ProcurementEvent extends DomainEvent permits ProcurementEvent.OrderConfirmed {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record OrderConfirmed(
            UUID orderId,
            UUID productRequestId,
            UUID buyerBusinessId,
            UUID supplierProfileId,
            String currency,
            BigDecimal total)
            implements ProcurementEvent {
        @Override
        public String type() {
            return "ORDER_CONFIRMED";
        }
    }
}
