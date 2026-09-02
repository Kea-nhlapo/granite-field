package za.co.trademesh.modules.procurement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConfirmedOrder(
        UUID id,
        UUID requestId,
        UUID sourceQuoteId,
        UUID buyerBusinessId,
        UUID supplierProfileId,
        UUID sourceDocumentId,
        OrderStatus status,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        String destinationLabel,
        double destinationLatitude,
        double destinationLongitude,
        Instant deliveryWindowStart,
        Instant deliveryWindowEnd,
        List<OrderLineItem> items,
        UUID confirmedByUserId,
        Instant confirmedAt) {

    public ConfirmedOrder {
        items = List.copyOf(items);
    }
}
